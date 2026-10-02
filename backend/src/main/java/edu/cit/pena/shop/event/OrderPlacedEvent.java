package edu.cit.pena.shop.event;

import java.time.LocalDateTime;
import java.util.List;

public class OrderPlacedEvent {

    private final String orderId;
    private final List<LineItem> items;
    private final LocalDateTime timestamp;

    public OrderPlacedEvent(String orderId, List<LineItem> items, LocalDateTime timestamp) {
        this.orderId = orderId;
        this.items = items;
        this.timestamp = timestamp;
    }

    public String getOrderId() {
        return orderId;
    }

    public List<LineItem> getItems() {
        return items;
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
