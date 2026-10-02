package edu.cit.pena.shop;

import java.util.List;

/**
 * Response DTO returned by OrderService and POST /api/orders.
 * Format:
 * {
 *   "orderId": 123,
 *   "status": "CONFIRMED" | "REJECTED",
 *   "reason": "...",
 *   "items": [ { "productId": "P100", "outcome": "RESERVED" | "REJECTED" }, ... ],
 *   "inventory": [ { "productId": "...", "name": "...", "stock": N }, ... ]
 * }
 */
public record OrderResponse(
        Long orderId,
        String status,
        String reason,
        List<OrderItemOutcome> items,
        List<InventorySnapshot> inventory
) {
    public static OrderResponse confirmed(
            Long orderId,
            String reason,
            List<OrderItemOutcome> items,
            List<InventorySnapshot> inventory) {
        return new OrderResponse(orderId, "CONFIRMED", reason, items, inventory);
    }

    public static OrderResponse rejected(
            Long orderId,
            String reason,
            List<OrderItemOutcome> items,
            List<InventorySnapshot> inventory) {
        return new OrderResponse(orderId, "REJECTED", reason, items, inventory);
    }
}