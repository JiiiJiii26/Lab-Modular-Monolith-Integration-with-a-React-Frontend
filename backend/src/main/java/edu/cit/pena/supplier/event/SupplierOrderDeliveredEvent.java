package edu.cit.pena.supplier.event;

import java.time.LocalDateTime;

public class SupplierOrderDeliveredEvent {
    private final String poNumber;
    private final String productId;
    private final int unitsRestocked;
    private final LocalDateTime deliveredAt;


    public SupplierOrderDeliveredEvent(String poNumber, String productId, int unitsRestocked, LocalDateTime deliveredAt) {
        this.poNumber = poNumber;
        this.productId = productId;
        this.unitsRestocked = unitsRestocked;
        this.deliveredAt = deliveredAt;
    }

    public String getPoNumber() {
        return poNumber;
    }

    public String getProductId() {
        return productId;
    }

    public int getUnitsRestocked() {
        return unitsRestocked;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

}
