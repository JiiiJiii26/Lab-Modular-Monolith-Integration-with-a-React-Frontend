package edu.cit.pena.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import edu.cit.pena.shared.events.OrderCancelledEvent;
import edu.cit.pena.shared.events.SupplierOrderDeliveredEvent;

/**
 * PACKAGE-PRIVATE listener. Pushes fresh stock after cancellations and supplier
 * deliveries. Tiangge orders publish stock after their remote decision succeeds.
 */
@Component
class TianggeStockSyncListener {

    private static final Logger log = LoggerFactory.getLogger(TianggeStockSyncListener.class);

    private final TianggeGateway gateway;

    TianggeStockSyncListener(TianggeGateway gateway) {
        this.gateway = gateway;
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
            if (gateway.publishCurrentStock()) {
                log.info("Stock published to Tiangge after inventory change");
            } else {
                log.warn("Stock publication to Tiangge failed after inventory change");
            }
        } catch (Exception e) {
            log.warn("Failed to publish stock after inventory change: {}", e.getMessage());
        }
    }
}