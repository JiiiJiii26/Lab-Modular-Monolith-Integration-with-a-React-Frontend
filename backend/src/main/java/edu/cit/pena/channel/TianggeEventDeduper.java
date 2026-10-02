package edu.cit.pena.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * PACKAGE-PRIVATE component that records processed events in their own transaction.
 * Being a separate Spring bean ensures REQUIRES_NEW propagation is honored.
 */
@Component
class TianggeEventDeduper {

    private static final Logger log = LoggerFactory.getLogger(TianggeEventDeduper.class);

    private final TianggeEventProcessedRepository repo;

    TianggeEventDeduper(TianggeEventProcessedRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    boolean isProcessed(String eventId) {
        return repo.existsById(eventId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    boolean markProcessed(String eventId) {
        if (repo.existsById(eventId)) {
            return false;
        }
        repo.save(new TianggeEventProcessed(eventId));
        log.debug("Marked event {} as processed (new)", eventId);
        return true;
    }
}