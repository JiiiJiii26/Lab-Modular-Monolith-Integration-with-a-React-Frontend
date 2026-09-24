package edu.cit.pena.inventory;

import edu.cit.pena.shared.events.SupplierOrderDeliveredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Package-private listener in the inventory module.
 * Listens for SupplierOrderDeliveredEvent and increments inventory stock.
 *
 * Boundary rule: imports ONLY the shared event and internal inventory repository.
 * Zero imports from edu.cit.pena.supplier.
 */
@Component
class SupplierDeliveryListener {

    private static final Logger log = LoggerFactory.getLogger(SupplierDeliveryListener.class);

    private final InventoryRepository inventoryRepository;

    SupplierDeliveryListener(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @EventListener
    @Transactional
    public void onDelivery(SupplierOrderDeliveredEvent event) {
        log.info("Processing delivery for supplier order {}: adding {} units to product {} (PO: {})",
                event.supplierOrderId(), event.units(), event.productId(), event.poNumber());

        int updated = inventoryRepository.incrementStock(event.productId(), event.units());
        log.info("Inventory updated for product {}: rows affected = {}", event.productId(), updated);
    }
}
