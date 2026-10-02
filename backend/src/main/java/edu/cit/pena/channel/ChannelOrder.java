package edu.cit.pena.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Persists orders originated from the Tiangge channel.
 * Maintains correlation between external Tiangge order IDs and internal shop order IDs,
 * as well as tracking backorders pending resolution.
 */
@Entity
@Table(name = "channel_orders")
class ChannelOrder {

    @Id
    @Column(name = "tiangge_order_id", length = 64, nullable = false)
    private String tianggeOrderId;

    @Column(name = "shop_order_id", length = 64)
    private String shopOrderId;

    @Column(name = "decision", length = 32)
    private String decision;

    @Column(name = "resolution", length = 32)
    private String resolution;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Column(name = "lines_json", columnDefinition = "TEXT")
    private String linesJson;

    @Column(name = "placed_at")
    private LocalDateTime placedAt;

    @Column(name = "decision_deadline")
    private LocalDateTime decisionDeadline;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public ChannelOrder() {
    }

    public ChannelOrder(String tianggeOrderId, String shopOrderId, String decision, String status, String linesJson, LocalDateTime placedAt, LocalDateTime decisionDeadline) {
        this.tianggeOrderId = tianggeOrderId;
        this.shopOrderId = shopOrderId;
        this.decision = decision;
        this.status = status;
        this.linesJson = linesJson;
        this.placedAt = placedAt;
        this.decisionDeadline = decisionDeadline;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public String getTianggeOrderId() {
        return tianggeOrderId;
    }

    public void setTianggeOrderId(String tianggeOrderId) {
        this.tianggeOrderId = tianggeOrderId;
    }

    public String getShopOrderId() {
        return shopOrderId;
    }

    public void setShopOrderId(String shopOrderId) {
        this.shopOrderId = shopOrderId;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLinesJson() {
        return linesJson;
    }

    public void setLinesJson(String linesJson) {
        this.linesJson = linesJson;
    }

    public LocalDateTime getPlacedAt() {
        return placedAt;
    }

    public void setPlacedAt(LocalDateTime placedAt) {
        this.placedAt = placedAt;
    }

    public LocalDateTime getDecisionDeadline() {
        return decisionDeadline;
    }

    public void setDecisionDeadline(LocalDateTime decisionDeadline) {
        this.decisionDeadline = decisionDeadline;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
