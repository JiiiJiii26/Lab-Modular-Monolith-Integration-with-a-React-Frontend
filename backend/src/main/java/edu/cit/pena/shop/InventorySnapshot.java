package edu.cit.pena.shop;

import edu.cit.pena.inventory.InventoryItem;

/**
 * Snapshot of inventory state returned alongside order results.
 */
public record InventorySnapshot(
        String productId,
        String name,
        int stock
) {
    public static InventorySnapshot from(InventoryItem item, String fallbackProductId, int fallbackStock) {
        if (item != null) {
            return new InventorySnapshot(item.getProductId(), item.getName(), item.getStock());
        }
        return new InventorySnapshot(fallbackProductId, "Unknown", fallbackStock);
    }
}
