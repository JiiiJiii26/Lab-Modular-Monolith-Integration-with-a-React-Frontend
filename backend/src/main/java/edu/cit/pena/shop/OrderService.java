package edu.cit.pena.shop;

import edu.cit.pena.inventory.InventoryItem;
import edu.cit.pena.inventory.InventoryService;
import edu.cit.pena.inventory.ReservationResult;
import edu.cit.pena.shared.events.OrderItemDto;
import edu.cit.pena.shared.events.OrderCancelledEvent;
import edu.cit.pena.shared.events.OrderPlacedEvent;
import edu.cit.pena.shared.events.OrderRejectedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/**
 * ARCHITECTURAL MODULE BOUNDARY ENFORCEMENT:
 *
 * OrderService strictly depends ONLY on the public 'InventoryService' interface and the shared events package.
 * It has NO compile-time knowledge or dependency on 'InventoryServiceImpl' or any class from 'edu.cit.pena.notification'.
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(
            OrderRepository orderRepository,
            InventoryService inventoryService,
            ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Places a multi-item order following the all-or-nothing transactional rule:
     * a. Load every line item's current stock via InventoryService.getItem().
     * b. If ANY line item exceeds available stock, persist an Order with status=REJECTED
     *    and NO order_items rows, publish an OrderRejected event, return immediately.
     * c. Only if ALL items pass validation, loop and call InventoryService.reserve() for each item.
     * d. Persist the Order with status=CONFIRMED and all order_items.
     * e. Publish OrderPlaced event.
     * f. If a reserve() call unexpectedly fails at step (c) (race condition), roll back
     *    the whole transaction by throwing an unchecked exception.
     */
    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {
        List<OrderItemRequest> reqItems = request != null ? request.getEffectiveItems() : List.of();

        if (reqItems.isEmpty()) {
            Order rejectedOrder = new Order("REJECTED", "Order must contain at least one item.");
            orderRepository.save(rejectedOrder);

            eventPublisher.publishEvent(new OrderRejectedEvent(
                    rejectedOrder.getOrderId(),
                    rejectedOrder.getReason(),
                    List.of()
            ));

            return OrderResponse.rejected(
                    rejectedOrder.getReason(),
                    List.of(),
                    getInventorySnapshots()
            );
        }

        // a. Load and validate every line item's current stock
        boolean anyFailure = false;
        List<String> failureReasons = new ArrayList<>();
        List<OrderItemDto> eventItems = new ArrayList<>();

        for (OrderItemRequest itemReq : reqItems) {
            eventItems.add(new OrderItemDto(itemReq.productId(), itemReq.quantity()));

            if (itemReq.quantity() <= 0) {
                anyFailure = true;
                failureReasons.add("Quantity for product " + itemReq.productId() + " must be greater than zero.");
                continue;
            }

            InventoryItem currentItem = inventoryService.getItem(itemReq.productId());
            if (currentItem == null) {
                anyFailure = true;
                failureReasons.add("Product not found: " + itemReq.productId());
            } else if (currentItem.getStock() < itemReq.quantity()) {
                anyFailure = true;
                failureReasons.add("Insufficient stock for " + currentItem.getName() + " (" + itemReq.productId()
                        + "): requested " + itemReq.quantity() + ", available " + currentItem.getStock() + ".");
            }
        }

        // b. If ANY line item fails, persist REJECTED order with NO order_items rows
        if (anyFailure) {
            String combinedReason = String.join("; ", failureReasons);
            Order rejectedOrder = new Order("REJECTED", combinedReason);
            // No order_items added to rejectedOrder!
            orderRepository.save(rejectedOrder);

            // Publish OrderRejected event
            eventPublisher.publishEvent(new OrderRejectedEvent(
                    rejectedOrder.getOrderId(),
                    combinedReason,
                    eventItems
            ));

            // On the REJECTED path, every line item in items[] must report outcome=REJECTED
            List<OrderItemOutcome> rejectedOutcomes = reqItems.stream()
                    .map(item -> new OrderItemOutcome(item.productId(), "REJECTED"))
                    .toList();

            return OrderResponse.rejected(
                    combinedReason,
                    rejectedOutcomes,
                    getInventorySnapshots()
            );
        }

        // c. Only if ALL items pass validation, reserve each item in-process
        for (OrderItemRequest itemReq : reqItems) {
            ReservationResult result = inventoryService.reserve(itemReq.productId(), itemReq.quantity());
            if (!result.isSuccess()) {
                // f. Roll back whole transaction if reserve unexpectedly fails (race condition)
                throw new IllegalStateException("Reservation failed unexpectedly for "
                        + itemReq.productId() + ": " + result.getReason());
            }
        }

        // d. Persist Order with status=CONFIRMED and all order_items
        String confirmReason = "Order confirmed with " + reqItems.size() + " line item(s).";
        Order confirmedOrder = new Order("CONFIRMED", confirmReason);
        for (OrderItemRequest itemReq : reqItems) {
            confirmedOrder.addItem(itemReq.productId(), itemReq.quantity());
        }
        orderRepository.save(confirmedOrder);

        // e. Publish OrderPlaced event
        eventPublisher.publishEvent(new OrderPlacedEvent(
                confirmedOrder.getOrderId(),
                eventItems
        ));

        // Only on the CONFIRMED path should items report outcome=RESERVED
        List<OrderItemOutcome> confirmedOutcomes = reqItems.stream()
                .map(item -> new OrderItemOutcome(item.productId(), "RESERVED"))
                .toList();

        return OrderResponse.confirmed(
                confirmReason,
                confirmedOutcomes,
                getInventorySnapshots()
        );
    }

    /**
     * Cancels an existing order and restocks all its line items:
     * - 404 if order not found
     * - 409 if order is already CANCELLED or REJECTED
     * - Otherwise: set status = CANCELLED, restock each line item, publish OrderCancelled event.
     */
    @Transactional
    public CancelOrderResult cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found with ID: " + orderId);
        }

        if ("CANCELLED".equalsIgnoreCase(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order " + orderId + " is already CANCELLED.");
        }

        if ("REJECTED".equalsIgnoreCase(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order " + orderId + " is REJECTED and cannot be cancelled.");
        }

        if (!"CONFIRMED".equalsIgnoreCase(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order " + orderId + " is not CONFIRMED and cannot be cancelled.");
        }

        // Set status to CANCELLED
        order.setStatus("CANCELLED");
        order.setReason("Order cancelled by user.");

        // Restock inventory for each line item
        List<OrderItemDto> itemDtos = new ArrayList<>();
        if (order.getItems() != null && !order.getItems().isEmpty()) {
            for (OrderItem item : order.getItems()) {
                inventoryService.restock(item.getProductId(), item.getQuantity());
                itemDtos.add(new OrderItemDto(item.getProductId(), item.getQuantity()));
            }
        }

        orderRepository.save(order);

        // Publish OrderCancelled event
        eventPublisher.publishEvent(new OrderCancelledEvent(order.getOrderId(), itemDtos));

        return CancelOrderResult.success(OrderDetailsDto.from(order));
    }

    /**
     * Retrieves all orders for the order history view (newest first).
     */
    @Transactional(readOnly = true)
    public List<OrderDetailsDto> getAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(OrderDetailsDto::from)
                .toList();
    }

    private List<InventorySnapshot> getInventorySnapshots() {
        return inventoryService.getAllItems().stream()
                .map(i -> new InventorySnapshot(i.getProductId(), i.getName(), i.getStock()))
                .toList();
    }
}
