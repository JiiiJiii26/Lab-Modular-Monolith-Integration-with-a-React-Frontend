package edu.cit.pena.shop;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @Column(name = "order_id", length = 50, nullable = false)
    private String orderId;

    @Column(name = "product_id", length = 50, nullable = true)
    private String productId;

    @Column(name = "quantity", nullable = true)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private OrderStatus status;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonManagedReference
    private List<OrderItem> items = new ArrayList<>();

    public Order() {
    }

    public Order(String orderId, OrderStatus status, String reason, LocalDateTime createdAt, List<OrderItem> items) {
        this.orderId = orderId;
        this.status = status;
        this.reason = reason;
        this.createdAt = createdAt;
        if (items != null) {
            items.forEach(this::addItem);
        }
    }

    public void addItem(OrderItem item) {
        this.items.add(item);
        item.setOrder(this);
        if (this.productId == null) {
            this.productId = item.getProductId();
            this.quantity = item.getQuantity();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String orderId;
        private OrderStatus status;
        private String reason;
        private LocalDateTime createdAt;
        private List<OrderItem> items = new ArrayList<>();

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

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder items(List<OrderItem> items) {
            this.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
            return this;
        }

        public Builder addItem(OrderItem item) {
            this.items.add(item);
            return this;
        }

        public Order build() {
            Order order = new Order();
            order.setOrderId(this.orderId);
            order.setStatus(this.status);
            order.setReason(this.reason);
            order.setCreatedAt(this.createdAt);
            for (OrderItem item : this.items) {
                order.addItem(item);
            }
            return order;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public int getTotalQuantity() {
        return items.stream().mapToInt(OrderItem::getQuantity).sum();
    }

    public void setItems(List<OrderItem> items) {
        this.items = new ArrayList<>();
        if (items != null) {
            items.forEach(this::addItem);
        }
    }
}

