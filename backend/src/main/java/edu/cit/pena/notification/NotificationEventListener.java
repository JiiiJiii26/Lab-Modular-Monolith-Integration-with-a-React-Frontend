package edu.cit.pena.notification;

import edu.cit.pena.inventory.event.LowStockEvent;
import edu.cit.pena.shop.event.OrderCancelledEvent;
import edu.cit.pena.shop.event.OrderPlacedEvent;
import edu.cit.pena.shop.event.OrderRejectedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Event listener for domain events within the monolith.
 * Architectural Boundary: Depends exclusively on domain event classes.
 * Never calls InventoryService or OrderService.
 */
@Component
@Transactional
class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationRepository notificationRepository;

    NotificationEventListener(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @EventListener
    public void handleOrderPlaced(OrderPlacedEvent event) {
        String itemsSummary = event.getItems().stream()
                .map(i -> i.getProductId() + " x" + i.getQuantity())
                .collect(Collectors.joining(", "));
        String message = String.format("Order %s confirmed (%s)", event.getOrderId(), itemsSummary);
        saveNotification(message, event.getTimestamp());
        log.info("[Notification] {}", message);
    }

    @EventListener
    public void handleOrderRejected(OrderRejectedEvent event) {
        String message = String.format("Order %s rejected: %s", event.getOrderId(), event.getReason());
        saveNotification(message, event.getTimestamp());
        log.warn("[Notification] {}", message);
    }

    @EventListener
    public void handleOrderCancelled(OrderCancelledEvent event) {
        String itemsSummary = event.getItems().stream()
                .map(i -> i.getProductId() + " x" + i.getQuantity())
                .collect(Collectors.joining(", "));
        String message = String.format("Order %s cancelled - restocked (%s)", event.getOrderId(), itemsSummary);
        saveNotification(message, event.getTimestamp());
        log.info("[Notification] {}", message);
    }

    @EventListener
    public void handleLowStock(LowStockEvent event) {
        String message = String.format("Product %s (%s) is low on stock (%d remaining, threshold: %d) - reorder needed",
                event.getProductId(), event.getProductName(), event.getRemainingStock(), event.getThreshold());
        saveNotification(message, event.getTimestamp());
        log.warn("[Notification] {}", message);
    }

    private void saveNotification(String message, LocalDateTime timestamp) {
        String id = "NOTIF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Notification notification = new Notification(
                id,
                message,
                timestamp != null ? timestamp : LocalDateTime.now()
        );
        notificationRepository.save(notification);
    }
}
