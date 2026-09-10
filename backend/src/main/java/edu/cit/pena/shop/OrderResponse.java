package edu.cit.pena.shop;

/**
 * Response DTO returned by OrderService and POST /api/orders.
 * Format: { "status": "CONFIRMED|REJECTED", "reason": "...", "inventory": { "productId": "...", "name": "...", "stock": N } }
 */
public record OrderResponse(
        String status,
        String reason,
        InventorySnapshot inventory
) {
    public static OrderResponse confirmed(String reason, InventorySnapshot inventory) {
        return new OrderResponse("CONFIRMED", reason, inventory);
    }

    public static OrderResponse rejected(String reason, InventorySnapshot inventory) {
        return new OrderResponse("REJECTED", reason, inventory);
    }
}
