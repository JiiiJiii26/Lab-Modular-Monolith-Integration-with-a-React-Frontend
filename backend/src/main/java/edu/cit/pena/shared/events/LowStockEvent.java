package edu.cit.pena.shared.events;

/**
 * Domain event published when inventory for a product drops below the low stock threshold.
 */
public record LowStockEvent(
        String productId,
        String name,
        int remainingStock,
        int threshold
) {}
