package edu.cit.pena.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * PACKAGE-PRIVATE JPA entity for tiangge_events_processed.
 */
@Entity
@Table(name = "tiangge_events_processed")
class TianggeEventProcessed {

    @Id
    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected TianggeEventProcessed() {}

    TianggeEventProcessed(String eventId) {
        this.eventId = eventId;
        this.processedAt = Instant.now();
    }

    String getEventId() { return eventId; }
    Instant getProcessedAt() { return processedAt; }
}
