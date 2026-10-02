package edu.cit.pena.shop;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import edu.cit.pena.inventory.InventoryService;
import edu.cit.pena.inventory.dto.InventoryItemDto;
import edu.cit.pena.inventory.dto.ReservationResult;
import edu.cit.pena.shop.dto.OrderItemOutcomeDto;
import edu.cit.pena.shop.dto.OrderItemRequest;
import edu.cit.pena.shop.dto.OrderRequest;
import edu.cit.pena.shop.dto.OrderResponse;
import edu.cit.pena.shop.event.OrderCancelledEvent;
import edu.cit.pena.shop.event.OrderPlacedEvent;
import edu.cit.pena.shop.event.OrderRejectedEvent;

@Service
public class OrderService {

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(InventoryService inventoryService,
                        OrderRepository orderRepository,
                        ApplicationEventPublisher eventPublisher) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {
        List<OrderItemRequest> itemRequests = request.getItems();
        if (itemRequests == null || itemRequests.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order must contain at least one item.");
        }

        String orderId = (request.getCustomOrderId() != null && !request.getCustomOrderId().trim().isEmpty())
                ? request.getCustomOrderId().trim()
                : "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LocalDateTime now = LocalDateTime.now();

        // Step 1: Pre-validation - Validate EVERY line item against current stock before reserving anything
        boolean allValid = true;
        String rejectionReason = null;
        List<OrderItemOutcomeDto> itemOutcomes = new ArrayList<>();
        Map<String, Integer> remainingInventoryMap = new HashMap<>();

        // Group quantities by productId if duplicate line items appear in request
        Map<String, Integer> requestedTotals = new LinkedHashMap<>();
        for (OrderItemRequest item : itemRequests) {
            requestedTotals.put(item.getProductId(),
                    requestedTotals.getOrDefault(item.getProductId(), 0) + item.getQuantity());
        }

        for (OrderItemRequest itemReq : itemRequests) {
            String productId = itemReq.getProductId();
            int qty = itemReq.getQuantity();

            if (qty <= 0) {
                allValid = false;
                rejectionReason = "Quantity must be greater than zero for product " + productId;
                itemOutcomes.add(new OrderItemOutcomeDto(productId, qty, "FAILED: Invalid quantity"));
                continue;
            }

            InventoryItemDto currentItem = inventoryService.getItem(productId);
            if (currentItem == null) {
                allValid = false;
                rejectionReason = String.format("Product with ID '%s' not found.", productId);
                itemOutcomes.add(new OrderItemOutcomeDto(productId, qty, "FAILED: Product not found"));
                remainingInventoryMap.put(productId, 0);
            } else if (currentItem.getStock() < requestedTotals.get(productId)) {
                allValid = false;
                rejectionReason = String.format("Insufficient stock for %s (%s): requested %d, available %d",
                        currentItem.getName(), productId, requestedTotals.get(productId), currentItem.getStock());
                itemOutcomes.add(new OrderItemOutcomeDto(productId, qty, "FAILED: Insufficient stock (available: " + currentItem.getStock() + ")"));
                remainingInventoryMap.put(productId, currentItem.getStock());
            } else {
                itemOutcomes.add(new OrderItemOutcomeDto(productId, qty, "PENDING"));
                remainingInventoryMap.put(productId, currentItem.getStock());
            }
        }

        OrderStatus status;
        Order order = new Order();
        order.setOrderId(orderId);
        order.setCreatedAt(now);

        for (OrderItemRequest itemReq : itemRequests) {
            order.addItem(new OrderItem(itemReq.getProductId(), itemReq.getQuantity()));
        }

        // Step 2: If any single item exceeds stock, the entire order is REJECTED and no items are reserved
        if (!allValid) {
            status = OrderStatus.REJECTED;
            order.setStatus(status);
            order.setReason(rejectionReason);

            // Mark pending items as REJECTED_ROLLBACK
            for (OrderItemOutcomeDto outcome : itemOutcomes) {
                if ("PENDING".equals(outcome.getOutcome())) {
                    outcome.setOutcome("REJECTED: Order rolled back");
                }
            }

            orderRepository.save(order);

            // Publish OrderRejectedEvent via ApplicationEventPublisher
            List<OrderRejectedEvent.LineItem> eventItems = itemRequests.stream()
                    .map(i -> new OrderRejectedEvent.LineItem(i.getProductId(), i.getQuantity()))
                    .collect(Collectors.toList());
            eventPublisher.publishEvent(new OrderRejectedEvent(orderId, eventItems, rejectionReason, now));

            Object inventorySummary = remainingInventoryMap.size() == 1
                    ? remainingInventoryMap.values().iterator().next()
                    : remainingInventoryMap;

            return OrderResponse.builder()
                    .orderId(orderId)
                    .status(status)
                    .reason(rejectionReason)
                    .items(itemOutcomes)
                    .inventory(inventorySummary)
                    .createdAt(now)
                    .build();
        }

        // Step 3: All items passed validation -> Proceed to reserve each item
        Map<String, Integer> reservedQuantities = new LinkedHashMap<>();
        for (int i = 0; i < itemRequests.size(); i++) {
            OrderItemRequest itemReq = itemRequests.get(i);
            ReservationResult result = inventoryService.reserve(itemReq.getProductId(), itemReq.getQuantity());
            if (result.isSuccess()) {
                itemOutcomes.get(i).setOutcome("RESERVED");
                remainingInventoryMap.put(itemReq.getProductId(), result.getRemainingStock());
                reservedQuantities.merge(itemReq.getProductId(), itemReq.getQuantity(), Integer::sum);
            } else {
                // A concurrent order may invalidate pre-validation. Compensate any
                // earlier reservations before recording the rejected order.
                reservedQuantities.forEach(inventoryService::restock);
                status = OrderStatus.REJECTED;
                rejectionReason = result.getMessage();
                order.setStatus(status);
                order.setReason(rejectionReason);
                orderRepository.save(order);

                return OrderResponse.builder()
                        .orderId(orderId)
                        .status(status)
                        .reason(rejectionReason)
                        .items(itemOutcomes)
                        .inventory(remainingInventoryMap)
                        .createdAt(now)
                        .build();
            }
        }

        status = OrderStatus.CONFIRMED;
        order.setStatus(status);
        order.setReason(null);
        orderRepository.save(order);

        // Publish OrderPlacedEvent via ApplicationEventPublisher
        List<OrderPlacedEvent.LineItem> eventItems = itemRequests.stream()
                .map(i -> new OrderPlacedEvent.LineItem(i.getProductId(), i.getQuantity()))
                .collect(Collectors.toList());
        eventPublisher.publishEvent(new OrderPlacedEvent(orderId, eventItems, now));

        Object inventorySummary = remainingInventoryMap.size() == 1
                ? remainingInventoryMap.values().iterator().next()
                : remainingInventoryMap;

        return OrderResponse.builder()
                .orderId(orderId)
                .status(status)
                .reason(null)
                .items(itemOutcomes)
                .inventory(inventorySummary)
                .createdAt(now)
                .build();
    }

    @Transactional
    public OrderResponse cancelOrder(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        String.format("Order with ID '%s' not found.", orderId)));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    String.format("Order '%s' is already CANCELLED.", orderId));
        }

        if (order.getStatus() == OrderStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    String.format("Order '%s' is REJECTED and cannot be cancelled.", orderId));
        }

        // Return reserved items back to inventory stock
        List<OrderItemOutcomeDto> outcomeDtos = new ArrayList<>();
        Map<String, Integer> updatedInventory = new HashMap<>();

        for (OrderItem item : order.getItems()) {
            inventoryService.restock(item.getProductId(), item.getQuantity());
            InventoryItemDto updatedItem = inventoryService.getItem(item.getProductId());
            int newStock = updatedItem != null ? updatedItem.getStock() : 0;
            updatedInventory.put(item.getProductId(), newStock);
            outcomeDtos.add(new OrderItemOutcomeDto(item.getProductId(), item.getQuantity(), "RESTOCKED"));
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        LocalDateTime now = LocalDateTime.now();
        List<OrderCancelledEvent.LineItem> eventItems = order.getItems().stream()
                .map(i -> new OrderCancelledEvent.LineItem(i.getProductId(), i.getQuantity()))
                .collect(Collectors.toList());
        eventPublisher.publishEvent(new OrderCancelledEvent(orderId, eventItems, now));

        Object inventorySummary = updatedInventory.size() == 1
                ? updatedInventory.values().iterator().next()
                : updatedInventory;

        return OrderResponse.builder()
                .orderId(order.getOrderId())
                .status(OrderStatus.CANCELLED)
                .reason("Order cancelled by user. Stock returned to inventory.")
                .items(outcomeDtos)
                .inventory(inventorySummary)
                .createdAt(order.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Order getOrderById(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        String.format("Order with ID '%s' not found.", orderId)));
    }
}

