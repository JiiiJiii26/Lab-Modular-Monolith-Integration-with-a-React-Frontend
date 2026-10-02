package edu.cit.pena.channel;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import edu.cit.pena.shared.events.OrderCancelledEvent;
import edu.cit.pena.shared.events.OrderPlacedEvent;
import edu.cit.pena.shared.events.SupplierOrderDeliveredEvent;

/**
 * PACKAGE-PRIVATE listener. Pushes fresh stock after cancellations and supplier
 * deliveries. Tiangge orders publish stock after their remote decision succeeds.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
class TianggeStockSyncListener {

    private static final Logger log = LoggerFactory.getLogger(TianggeStockSyncListener.class);
    private static final ThreadLocal<Boolean> suppressForCurrentThread =
            ThreadLocal.withInitial(() -> false);

    private final TianggeGateway gateway;

    TianggeStockSyncListener(TianggeGateway gateway) {
        this.gateway = gateway;
    }

    static <T> T suppressForCurrentThread(Supplier<T> action) {
        boolean previous = suppressForCurrentThread.get();
        suppressForCurrentThread.set(true);
        try {
            return action.get();
        } finally {
            suppressForCurrentThread.set(previous);
        }
    }

    static void suppressForCurrentThread(Runnable action) {
        suppressForCurrentThread(() -> {
            action.run();
            return null;
        });
    }

    @EventListener
    void onOrderPlaced(OrderPlacedEvent event) {
        if (!suppressForCurrentThread.get()) {
            publish();
        }
    }

    @EventListener
    void onOrderCancelled(OrderCancelledEvent event) {
        if (!suppressForCurrentThread.get()) {
            publish();
        }
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