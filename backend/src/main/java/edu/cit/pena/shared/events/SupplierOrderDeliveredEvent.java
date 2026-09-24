package edu.cit.pena.shared.events;

/**
 * Domain event published when a supplier purchase order transitions to DELIVERED.
 * Consumed by the inventory module to increment stock and notify relevant listeners.
 */
public record SupplierOrderDeliveredEvent(
        Long supplierOrderId,
        String productId,
        int units,
        String poNumber
) {}
