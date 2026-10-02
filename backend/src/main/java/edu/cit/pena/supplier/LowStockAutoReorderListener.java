package edu.cit.pena.supplier;

import edu.cit.pena.inventory.event.LowStockEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class LowStockAutoReorderListener {

    private static final Logger log = LoggerFactory.getLogger(LowStockAutoReorderListener.class);
    private final SupplierGateway supplierGateway;

    public LowStockAutoReorderListener(SupplierGateway supplierGateway) {
        this.supplierGateway = supplierGateway;
    }

    @EventListener
    public void onLowStock(LowStockEvent event) {
        log.info("Low stock detected for {} ({}) - current: {}, threshold: {}. Placing replenishment order...",
                event.getProductName(), event.getProductId(), event.getRemainingStock(), event.getThreshold());

        // Calculate replenish target (e.g. order 20 units)
        int unitsNeeded = 20;
        try {
            supplierGateway.orderReplenishment(event.getProductId(), unitsNeeded);
        } catch (Exception ex) {
            log.error("Failed to initiate replenishment for product {}: {}", event.getProductId(), ex.getMessage());
        }
    }
}