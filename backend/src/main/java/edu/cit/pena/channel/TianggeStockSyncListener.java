package edu.cit.pena.channel;

import edu.cit.pena.inventory.InventoryService;
import edu.cit.pena.inventory.dto.InventoryItemDto;
import edu.cit.pena.shop.event.OrderCancelledEvent;
import edu.cit.pena.shop.event.OrderPlacedEvent;
import edu.cit.pena.supplier.event.SupplierOrderDeliveredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Event-driven listener that synchronizes inventory stock levels with Tiangge.
 * Listens to domain events (order placed, order cancelled, supplier delivery)
 * and reacts immediately by sending PUT /stock.
 * Never publishes on a timer.
 */
@Component
class TianggeStockSyncListener {

    private static final Logger log = LoggerFactory.getLogger(TianggeStockSyncListener.class);

    private final InventoryService inventoryService;
    private final TianggeClient httpClient;

    public TianggeStockSyncListener(InventoryService inventoryService, TianggeClient httpClient) {
        this.inventoryService = inventoryService;
        this.httpClient = httpClient;
    }

    public synchronized void publishCurrentStock() {
        try {
            List<InventoryItemDto> items = inventoryService.getAllItems();
            List<Models.StockItem> stockPayload = items.stream()
                    .map(item -> new Models.StockItem(item.getProductId(), item.getStock()))
                    .toList();

            log.info("Synchronizing stock with Tiangge: {}", stockPayload);
            httpClient.publishStock(stockPayload);
            log.info("Stock successfully synchronized with Tiangge.");
        } catch (Exception ex) {
            log.warn("Failed synchronizing stock with Tiangge: {}", ex.getMessage());
        }
    }

    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        if (Boolean.TRUE.equals(TianggeOrderProcessor.CHANNEL_PROCESSING.get())) {
            log.info("[Event] OrderPlacedEvent {} suppressed during channel order processing (will publish stock after decision).", event.getOrderId());
            return;
        }
        log.info("[Event] OrderPlacedEvent {} received: updating Tiangge stock numbers.", event.getOrderId());
        publishCurrentStock();
    }

    @EventListener
    public void onOrderCancelled(OrderCancelledEvent event) {
        if (Boolean.TRUE.equals(TianggeOrderProcessor.CHANNEL_PROCESSING.get())) {
            log.info("[Event] OrderCancelledEvent {} suppressed during channel cancellation processing (will publish stock after confirmation).", event.getOrderId());
            return;
        }
        log.info("[Event] OrderCancelledEvent {} received: updating Tiangge stock numbers.", event.getOrderId());
        publishCurrentStock();
    }

    @EventListener
    @Order(3)
    public void onSupplierDelivery(SupplierOrderDeliveredEvent event) {
        log.info("[Event] SupplierOrderDeliveredEvent {} received: updating Tiangge stock numbers.", event.getPoNumber());
        publishCurrentStock();
    }
}
