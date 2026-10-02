package edu.cit.pena.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Persists the latest sequence number processed from the Tiangge order feed.
 * Guarantees that app restarts resume reading the feed without restarting from zero.
 */
@Entity
@Table(name = "channel_feed_cursor")
class FeedCursor {

    @Id
    @Column(name = "id", length = 50)
    private String id = "TIANGGE_FEED_CURSOR";

    @Column(name = "cursor_value", nullable = false)
    private Long cursorValue = 0L;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public FeedCursor() {
    }

    public FeedCursor(String id, Long cursorValue, LocalDateTime updatedAt) {
        this.id = id;
        this.cursorValue = cursorValue;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getCursorValue() {
        return cursorValue;
    }

    public void setCursorValue(Long cursorValue) {
        this.cursorValue = cursorValue;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
