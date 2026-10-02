package edu.cit.pena.inventory.event;

import java.time.LocalDateTime;

public class LowStockEvent {

    private final String productId;
    private final String productName;
    private final int remainingStock;
    private final int threshold;
    private final LocalDateTime timestamp;

    public LowStockEvent(String productId, String productName, int remainingStock, int threshold, LocalDateTime timestamp) {
        this.productId = productId;
        this.productName = productName;
        this.remainingStock = remainingStock;
        this.threshold = threshold;
        this.timestamp = timestamp;
    }

    public String getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public int getRemainingStock() {
        return remainingStock;
    }

    public int getThreshold() {
        return threshold;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
