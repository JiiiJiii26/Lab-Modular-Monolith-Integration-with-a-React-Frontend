package edu.cit.pena.supplier;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * PACKAGE-PRIVATE entity for supplier_orders.
 * Stores only internal domain enum values for status, never raw supplier codes.
 */
@Entity
@Table(name = "supplier_orders")
class SupplierOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "buyer_ref", nullable = false, unique = true)
    private String buyerRef;

    @Column(name = "request_id", nullable = false, unique = true)
    private String requestId;

    @Column(name = "po_number")
    private String poNumber;

    @Column(name = "cases", nullable = false)
    private int cases;

    @Column(name = "units", nullable = false)
    private int units;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SupplierOrderStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    SupplierOrder() {}

    SupplierOrder(String productId, String buyerRef, String requestId, int cases, int units, SupplierOrderStatus status) {
        this.productId = productId;
        this.buyerRef = buyerRef;
        this.requestId = requestId;
        this.cases = cases;
        this.units = units;
        this.status = status;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    Long getId() { return id; }
    void setId(Long id) { this.id = id; }

    String getProductId() { return productId; }
    void setProductId(String productId) { this.productId = productId; }

    String getBuyerRef() { return buyerRef; }
    void setBuyerRef(String buyerRef) { this.buyerRef = buyerRef; }

    String getRequestId() { return requestId; }
    void setRequestId(String requestId) { this.requestId = requestId; }

    String getPoNumber() { return poNumber; }
    void setPoNumber(String poNumber) { this.poNumber = poNumber; }

    int getCases() { return cases; }
    void setCases(int cases) { this.cases = cases; }

    int getUnits() { return units; }
    void setUnits(int units) { this.units = units; }

    SupplierOrderStatus getStatus() { return status; }
    void setStatus(SupplierOrderStatus status) { this.status = status; }

    Instant getCreatedAt() { return createdAt; }
    void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    Instant getUpdatedAt() { return updatedAt; }
    void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
