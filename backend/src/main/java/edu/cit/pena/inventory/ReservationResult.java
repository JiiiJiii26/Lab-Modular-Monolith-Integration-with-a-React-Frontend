package edu.cit.pena.inventory;

/**
 * Result object returned by InventoryService.reserve carrying
 * success/failure flag, an explanation reason, and remaining stock.
 */
public record ReservationResult(
        boolean success,
        String reason,
        int remainingStock,
        InventoryItem item
) {
    public static ReservationResult success(String reason, int remainingStock, InventoryItem item) {
        return new ReservationResult(true, reason, remainingStock, item);
    }

    public static ReservationResult failure(String reason, int remainingStock, InventoryItem item) {
        return new ReservationResult(false, reason, remainingStock, item);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getReason() {
        return reason;
    }

    public int getRemainingStock() {
        return remainingStock;
    }

    public InventoryItem getItem() {
        return item;
    }
}
