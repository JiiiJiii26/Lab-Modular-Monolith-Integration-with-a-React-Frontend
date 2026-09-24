package edu.cit.pena.supplier;

import edu.cit.pena.shared.events.LowStockEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * PACKAGE-PRIVATE event listener in the supplier module.
 * Subscribes to LowStockEvent published by the inventory module and automatically
 * triggers restock requests via the SupplierGateway.
 *
 * Neither InventoryService nor NotificationListener are modified;
 * the supplier module integrates purely via domain events.
 */
@Component
class SupplierReorderListener {

    private static final Logger log = LoggerFactory.getLogger(SupplierReorderListener.class);

    private final SupplierGateway supplierGateway;

    SupplierReorderListener(SupplierGateway supplierGateway) {
        this.supplierGateway = supplierGateway;
    }

    @EventListener
    public void onLowStock(LowStockEvent event) {
        log.info("Received LowStockEvent for product '{}' (remainingStock={}, threshold={})",
                event.productId(), event.remainingStock(), event.threshold());

        // Reorder quantity calculation:
        // When stock drops below the threshold, we order enough to safely restore inventory
        // well above the threshold. We calculate the deficit up to 3x the threshold,
        // with a sensible minimum batch of 20 units:
        // unitsNeeded = max(threshold * 3 - remainingStock, 20).
        int targetStock = Math.max(event.threshold() * 3, 20);
        int unitsNeeded = Math.max(targetStock - event.remainingStock(), 10);

        log.info("Triggering automatic restock request for product '{}': unitsNeeded={}",
                event.productId(), unitsNeeded);

        SupplierOrderResult result = supplierGateway.requestRestock(event.productId(), unitsNeeded);
        log.info("Restock request result for product '{}': status={}, buyerRef={}, message={}",
                event.productId(), result.status(), result.buyerRef(), result.message());
    }
}
