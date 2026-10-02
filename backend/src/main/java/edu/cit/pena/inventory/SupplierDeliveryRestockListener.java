package edu.cit.pena.inventory;

import edu.cit.pena.supplier.event.SupplierOrderDeliveredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
class SupplierDeliveryRestockListener {

    private static final Logger log = LoggerFactory.getLogger(SupplierDeliveryRestockListener.class);
    private final InventoryService inventoryService;

    public SupplierDeliveryRestockListener(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @EventListener
    @Order(1)
    public void onSupplierOrderDelivered(SupplierOrderDeliveredEvent event) {
        log.info("Received delivery event for PO {}: restocking product {} with {} units",
                event.getPoNumber(), event.getProductId(), event.getUnitsRestocked());
        inventoryService.restock(event.getProductId(), event.getUnitsRestocked());
    }
}