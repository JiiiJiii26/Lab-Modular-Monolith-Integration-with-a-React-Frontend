package edu.cit.pena.shop;

import java.util.List;

/**
 * Response DTO returned by OrderService and POST /api/orders.
 * Format:
 * {
 *   "status": "CONFIRMED" | "REJECTED",
 *   "reason": "...",
 *   "items": [ { "productId": "P100", "outcome": "RESERVED" | "REJECTED" }, ... ],
 *   "inventory": [ { "productId": "...", "name": "...", "stock": N }, ... ]
 * }
 */
public record OrderResponse(
        String status,
        String reason,
        List<OrderItemOutcome> items,
        List<InventorySnapshot> inventory
) {
    public static OrderResponse confirmed(
            String reason,
            List<OrderItemOutcome> items,
            List<InventorySnapshot> inventory) {
        return new OrderResponse("CONFIRMED", reason, items, inventory);
    }

    public static OrderResponse rejected(
            String reason,
            List<OrderItemOutcome> items,
            List<InventorySnapshot> inventory) {
        return new OrderResponse("REJECTED", reason, items, inventory);
    }
}
