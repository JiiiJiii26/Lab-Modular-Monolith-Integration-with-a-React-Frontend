package edu.cit.pena.notification;

import edu.cit.pena.shared.events.LowStockEvent;
import edu.cit.pena.shared.events.OrderCancelledEvent;
import edu.cit.pena.shared.events.OrderPlacedEvent;
import edu.cit.pena.shared.events.OrderRejectedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Event listener handling domain events for the notification module.
 *
 * ARCHITECTURAL DESIGN NOTE:
 * Listeners are intentionally NOT marked with @Async. They execute synchronously within the caller's
 * transaction boundary so that if an order transaction rolls back (e.g. during an unexpected reservation failure),
 * any associated notification record writes are automatically rolled back with the transaction,
 * guaranteeing data consistency without orphan notifications.
 *
 * MODULE BOUNDARY ENFORCEMENT:
 * This class imports only shared events from 'edu.cit.pena.shared.events' and internal classes.
 * It strictly DOES NOT import from 'edu.cit.pena.shop' or 'edu.cit.pena.inventory'.
 */
@Component
public class NotificationListener {

    private final NotificationRepository notificationRepository;

    public NotificationListener(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        String message = "Order " + event.orderId() + " confirmed";
        notificationRepository.save(new Notification(message, "ORDER_CONFIRMED"));
    }

    @EventListener
    public void onOrderRejected(OrderRejectedEvent event) {
        String message = "Order " + event.orderId() + " rejected: " + event.reason();
        notificationRepository.save(new Notification(message, "ORDER_REJECTED"));
    }

    @EventListener
    public void onOrderCancelled(OrderCancelledEvent event) {
        String message = "Order " + event.orderId() + " cancelled";
        notificationRepository.save(new Notification(message, "ORDER_CANCELLED"));
    }

    @EventListener
    public void onLowStock(LowStockEvent event) {
        String message = "Reorder needed: " + event.name() + " (" + event.productId() + ") stock " + event.remainingStock();
        notificationRepository.save(new Notification(message, "LOW_STOCK"));
    }
}
