package edu.cit.pena.inventory;

/**
 * Result object returned by InventoryService.restock carrying
 * success/failure flag, explanation reason, remaining stock, and InventoryItem snapshot.
 */
public record RestockResult(
        boolean success,
        String reason,
        int remainingStock,
        InventoryItem item
) {
    public static RestockResult success(String reason, int remainingStock, InventoryItem item) {
        return new RestockResult(true, reason, remainingStock, item);
    }

    public static RestockResult failure(String reason, int remainingStock, InventoryItem item) {
        return new RestockResult(false, reason, remainingStock, item);
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
