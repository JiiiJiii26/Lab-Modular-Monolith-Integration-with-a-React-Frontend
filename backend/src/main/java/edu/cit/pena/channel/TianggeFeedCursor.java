package edu.cit.pena.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * PACKAGE-PRIVATE JPA entity for tiangge_feed_cursor (single row, id=1).
 */
@Entity
@Table(name = "tiangge_feed_cursor")
class TianggeFeedCursor {

    @Id
    @Column(name = "id")
    private int id;

    @Column(name = "last_cursor", nullable = false)
    private long lastCursor;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TianggeFeedCursor() {}

    TianggeFeedCursor(int id, long lastCursor, Instant updatedAt) {
        this.id = id;
        this.lastCursor = lastCursor;
        this.updatedAt = updatedAt;
    }

    int getId() { return id; }

    long getLastCursor() { return lastCursor; }

    void setLastCursor(long lastCursor) {
        this.lastCursor = lastCursor;
        this.updatedAt = Instant.now();
    }

    Instant getUpdatedAt() { return updatedAt; }

    void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
