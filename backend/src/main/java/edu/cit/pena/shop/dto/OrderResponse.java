package edu.cit.pena.shop.dto;

import edu.cit.pena.shop.OrderStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class OrderResponse {

    private String orderId;
    private OrderStatus status;
    private String reason;
    private List<OrderItemOutcomeDto> items = new ArrayList<>();
    private Object inventory;
    private LocalDateTime createdAt;

    public OrderResponse() {
    }

    public OrderResponse(String orderId, OrderStatus status, String reason, List<OrderItemOutcomeDto> items, Object inventory, LocalDateTime createdAt) {
        this.orderId = orderId;
        this.status = status;
        this.reason = reason;
        this.items = items != null ? items : new ArrayList<>();
        this.inventory = inventory;
        this.createdAt = createdAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String orderId;
        private OrderStatus status;
        private String reason;
        private List<OrderItemOutcomeDto> items = new ArrayList<>();
        private Object inventory;
        private LocalDateTime createdAt;

        public Builder orderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder status(OrderStatus status) {
            this.status = status;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder items(List<OrderItemOutcomeDto> items) {
            this.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
            return this;
        }

        public Builder addItem(OrderItemOutcomeDto item) {
            this.items.add(item);
            return this;
        }

        public Builder inventory(Object inventory) {
            this.inventory = inventory;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public OrderResponse build() {
            return new OrderResponse(orderId, status, reason, items, inventory, createdAt);
        }
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public List<OrderItemOutcomeDto> getItems() {
        return items;
    }

    public void setItems(List<OrderItemOutcomeDto> items) {
        this.items = items;
    }

    public Object getInventory() {
        return inventory;
    }

    public void setInventory(Object inventory) {
        this.inventory = inventory;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // Backward compatibility helper methods
    public String getProductId() {
        return (items != null && !items.isEmpty()) ? items.get(0).getProductId() : null;
    }

    public Integer getQuantity() {
        return (items != null && !items.isEmpty()) ? items.get(0).getQuantity() : null;
    }
}

