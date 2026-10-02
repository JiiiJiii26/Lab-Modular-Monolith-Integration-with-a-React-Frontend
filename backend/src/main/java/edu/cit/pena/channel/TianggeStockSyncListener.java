package edu.cit.pena.channel;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import edu.cit.pena.inventory.InventoryItem;
import edu.cit.pena.inventory.InventoryService;
import edu.cit.pena.shared.events.OrderCancelledEvent;
import edu.cit.pena.shared.events.OrderPlacedEvent;
import edu.cit.pena.shared.events.SupplierOrderDeliveredEvent;

/**
 * PACKAGE-PRIVATE listener. Pushes fresh stock to Tiangge whenever Inventory
 * changes through any channel (Tiangge order, React UI order, cancellation,
 * supplier delivery).
 */
@Component
class TianggeStockSyncListener {

    private static final Logger log = LoggerFactory.getLogger(TianggeStockSyncListener.class);

    private final TianggeGateway gateway;
    private final InventoryService inventoryService;

    TianggeStockSyncListener(TianggeGateway gateway, InventoryService inventoryService) {
        this.gateway = gateway;
        this.inventoryService = inventoryService;
    }

    @EventListener
    void onOrderPlaced(OrderPlacedEvent event) {
        publish();
    }

    @EventListener
    void onOrderCancelled(OrderCancelledEvent event) {
        publish();
    }

    @EventListener
    void onDelivery(SupplierOrderDeliveredEvent event) {
        publish();
    }

    private void publish() {
        try {
            Map<String, Integer> stock = new HashMap<>();
            for (InventoryItem item : inventoryService.getAllItems()) {
                stock.put(item.getProductId(), item.getStock());
            }
            gateway.publishStock(stock);
            log.info("Stock published to Tiangge after inventory change: {}", stock);
        } catch (Exception e) {
            log.warn("Failed to publish stock after inventory change: {}", e.getMessage());
        }
    }
}