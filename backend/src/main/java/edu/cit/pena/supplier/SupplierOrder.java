package edu.cit.pena.supplier;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "supplier_orders")
class SupplierOrder {

    @Id
    @Column(name = "id", length = 50)
    private String id;

    @Column(name = "product_id", nullable = false, length = 50)
    private String productId;

    @Column(name = "buyer_ref", nullable = false, unique = true, length = 40)
    private String buyerRef;

    @Column(name = "request_id", nullable = false, length = 80)
    private String requestId;

    @Column(name = "po_number", length = 50)
    private String poNumber;

    @Column(name = "cases", nullable = false)
    private int cases;

    @Column(name = "units", nullable = false)
    private int units;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SupplierOrderStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public SupplierOrder() {}

    public SupplierOrder(String id, String productId, String buyerRef, String requestId, int cases, int units, SupplierOrderStatus status) {
        this.id = id;
        this.productId = productId;
        this.buyerRef = buyerRef;
        this.requestId = requestId;
        this.cases = cases;
        this.units = units;
        this.status = status;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public String getProductId() { return productId; }
    public String getBuyerRef() { return buyerRef; }
    public String getRequestId() { return requestId; }
    public String getPoNumber() { return poNumber; }
    public void setPoNumber(String poNumber) { this.poNumber = poNumber; }
    public int getCases() { return cases; }
    public int getUnits() { return units; }
    public SupplierOrderStatus getStatus() { return status; }
    public void setStatus(SupplierOrderStatus status) { 
        this.status = status; 
        this.updatedAt = LocalDateTime.now();
    }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}