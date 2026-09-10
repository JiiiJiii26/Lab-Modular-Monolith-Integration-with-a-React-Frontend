package edu.cit.pena.shop;

import edu.cit.pena.inventory.InventoryItem;
import edu.cit.pena.inventory.InventoryService;
import edu.cit.pena.inventory.ReservationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ARCHITECTURAL MODULE BOUNDARY ENFORCEMENT:
 *
 * OrderService strictly depends ONLY on the public 'InventoryService' interface via constructor injection.
 * It has NO compile-time knowledge or dependency on 'InventoryServiceImpl' (which is package-private
 * inside 'edu.cit.pena.inventory') or 'InventoryRepository'.
 *
 * Why this matters:
 * 1. Encapsulation & Loose Coupling: The Order module cannot tamper with internal inventory state,
 *    bypass business rules (e.g. reserving stock without validation), or leak JPA transactions
 *    across domain boundaries.
 * 2. In-process Efficiency with Clean Separation: Calls to reserve(...) happen in-process via Java method
 *    invocation (zero network latency, zero serialization overhead), yet the contract is cleanly abstracted.
 * 3. Extraction Readiness: If the inventory module is later decomposed into a standalone microservice,
 *    OrderService requires zero code changes beyond swapping the InventoryService implementation from an
 *    in-process bean to an HTTP client (e.g. OpenFeign or RestClient).
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;

    public OrderService(OrderRepository orderRepository, InventoryService inventoryService) {
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {
        if (request == null || request.productId() == null || request.productId().isBlank()) {
            Order order = new Order("UNKNOWN", request != null ? request.quantity() : 0, "REJECTED", "Invalid product ID.");
            orderRepository.save(order);
            return OrderResponse.rejected("Invalid product ID.", new InventorySnapshot("UNKNOWN", "Unknown", 0));
        }

        if (request.quantity() <= 0) {
            InventoryItem currentItem = inventoryService.getItem(request.productId());
            InventorySnapshot snapshot = InventorySnapshot.from(
                    currentItem,
                    request.productId(),
                    currentItem != null ? currentItem.getStock() : 0
            );
            Order order = new Order(request.productId(), request.quantity(), "REJECTED", "Order quantity must be greater than zero.");
            orderRepository.save(order);
            return OrderResponse.rejected("Order quantity must be greater than zero.", snapshot);
        }

        // Call InventoryService in-process
        ReservationResult reservationResult = inventoryService.reserve(request.productId(), request.quantity());

        if (reservationResult.isSuccess()) {
            Order confirmedOrder = new Order(
                    request.productId(),
                    request.quantity(),
                    "CONFIRMED",
                    reservationResult.getReason()
            );
            orderRepository.save(confirmedOrder);

            InventorySnapshot snapshot = InventorySnapshot.from(
                    reservationResult.getItem(),
                    request.productId(),
                    reservationResult.getRemainingStock()
            );

            return OrderResponse.confirmed(reservationResult.getReason(), snapshot);
        } else {
            Order rejectedOrder = new Order(
                    request.productId(),
                    request.quantity(),
                    "REJECTED",
                    reservationResult.getReason()
            );
            orderRepository.save(rejectedOrder);

            // Fetch current snapshot if item wasn't returned in result
            InventoryItem item = reservationResult.getItem() != null
                    ? reservationResult.getItem()
                    : inventoryService.getItem(request.productId());

            InventorySnapshot snapshot = InventorySnapshot.from(
                    item,
                    request.productId(),
                    reservationResult.getRemainingStock()
            );

            return OrderResponse.rejected(reservationResult.getReason(), snapshot);
        }
    }
}
