package edu.cit.pena.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * PACKAGE-PRIVATE JPA entity for tiangge_order_map.
 */
@Entity
@Table(name = "tiangge_order_map")
class TianggeOrderMap {

    @Id
    @Column(name = "tiangge_order_id", nullable = false)
    private String tianggeOrderId;

    @Column(name = "shop_order_id", nullable = false)
    private long shopOrderId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "decision_deadline")
    private Instant decisionDeadline;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TianggeOrderMap() {}

    TianggeOrderMap(String tianggeOrderId, long shopOrderId, String status, Instant decisionDeadline) {
        this.tianggeOrderId = tianggeOrderId;
        this.shopOrderId = shopOrderId;
        this.status = status;
        this.decisionDeadline = decisionDeadline;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    String getTianggeOrderId() { return tianggeOrderId; }
    long getShopOrderId() { return shopOrderId; }
    String getStatus() { return status; }

    void setStatus(String status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    Instant getDecisionDeadline() { return decisionDeadline; }
    void setDecisionDeadline(Instant decisionDeadline) { this.decisionDeadline = decisionDeadline; }

    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }
    void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
