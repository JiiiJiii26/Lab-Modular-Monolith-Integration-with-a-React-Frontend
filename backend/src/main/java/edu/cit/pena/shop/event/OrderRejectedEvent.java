package edu.cit.pena.shop.event;

import java.time.LocalDateTime;
import java.util.List;

public class OrderRejectedEvent {

    private final String orderId;
    private final List<LineItem> items;
    private final String reason;
    private final LocalDateTime timestamp;

    public OrderRejectedEvent(String orderId, List<LineItem> items, String reason, LocalDateTime timestamp) {
        this.orderId = orderId;
        this.items = items;
        this.reason = reason;
        this.timestamp = timestamp;
    }

    public String getOrderId() {
        return orderId;
    }

    public List<LineItem> getItems() {
        return items;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public static class LineItem {
        private final String productId;
        private final int quantity;

        public LineItem(String productId, int quantity) {
            this.productId = productId;
            this.quantity = quantity;
        }

        public String getProductId() {
            return productId;
        }

        public int getQuantity() {
            return quantity;
        }
    }
}
