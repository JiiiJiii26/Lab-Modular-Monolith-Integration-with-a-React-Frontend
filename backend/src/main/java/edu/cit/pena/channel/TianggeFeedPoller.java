package edu.cit.pena.channel;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Package-private background poller that checks the Tiangge order feed every few seconds.
 * Stores the cursor durably to resume seamlessly upon application restart.
 */
@Component
@ConditionalOnProperty(name = "tiangge.feed.enabled", havingValue = "true", matchIfMissing = true)
class TianggeFeedPoller {

    private static final Logger log = LoggerFactory.getLogger(TianggeFeedPoller.class);
    private static final String CURSOR_ID = "TIANGGE_FEED_CURSOR";

    private final TianggeClient httpClient;
    private final TianggeOrderProcessor orderProcessor;
    private final FeedCursorRepository feedCursorRepository;
    private final ChannelGateway channelGateway;

    public TianggeFeedPoller(
            TianggeClient httpClient,
            TianggeOrderProcessor orderProcessor,
            FeedCursorRepository feedCursorRepository,
            ChannelGateway channelGateway) {
        this.httpClient = httpClient;
        this.orderProcessor = orderProcessor;
        this.feedCursorRepository = feedCursorRepository;
        this.channelGateway = channelGateway;
    }

    @Scheduled(initialDelay = 4000, fixedDelay = 2500)
    public synchronized void pollFeed() {
        if (!channelGateway.isLive()) {
            return;
        }

        FeedCursor cursorEntity = feedCursorRepository.findById(CURSOR_ID).orElse(null);
        if (cursorEntity == null) {
            // A fresh database must not replay already-decided historical orders.
            // Walk all existing pages first; later polls process only new events.
            long currentCursor = 0L;
            for (int pageNumber = 0; pageNumber < 1000; pageNumber++) {
                Models.FeedResponse bootstrap = httpClient.getFeed(currentCursor, 50);
                if (bootstrap == null || bootstrap.events() == null || bootstrap.events().isEmpty()) {
                    break;
                }
                long lastEventSequence = bootstrap.events().stream()
                        .mapToLong(Models.FeedEvent::seq)
                        .max()
                        .orElse(currentCursor);
                long nextCursor = bootstrap.nextCursor() != null
                        ? bootstrap.nextCursor()
                        : currentCursor;
                long advancedCursor = Math.max(lastEventSequence, nextCursor);
                if (advancedCursor <= currentCursor) {
                    break;
                }
                currentCursor = advancedCursor;
            }
            cursorEntity = feedCursorRepository.save(
                new FeedCursor(CURSOR_ID, currentCursor, LocalDateTime.now()));
            log.info("Initialized feed cursor at {} without replaying historical events", currentCursor);
            return;
        }

        long after = cursorEntity.getCursorValue();
        Models.FeedResponse feedResponse = httpClient.getFeed(after, 50);

        if (feedResponse == null) {
            return;
        }

        boolean batchSuccessful = true;
        if (feedResponse.events() != null && !feedResponse.events().isEmpty()) {
            log.info("Received {} events from Tiangge feed (after={})", feedResponse.events().size(), after);

            for (Models.FeedEvent event : feedResponse.events()) {
                if (event.seq() <= after) {
                    continue; // Skip events already recorded past cursor
                }

                boolean processed = false;
                try {
                    if ("ORDER_PLACED".equalsIgnoreCase(event.type())) {
                        orderProcessor.processOrderPlaced(event);
                    } else if ("ORDER_CANCELLED".equalsIgnoreCase(event.type())) {
                        orderProcessor.processOrderCancelled(event);
                    } else {
                        log.debug("Ignoring unrecognized feed event type: {}", event.type());
                    }
                    processed = true;
                } catch (Exception ex) {
                    log.error("Error processing feed event {}: {}", event.eventId(), ex.getMessage(), ex);
                }

                if (!processed) {
                    log.warn("Keeping feed cursor at {} so event {} can be retried", after, event.eventId());
                    batchSuccessful = false;
                    break;
                }

                // Advance the cursor only after the event has been fully handled.
                cursorEntity.setCursorValue(event.seq());
                cursorEntity.setUpdatedAt(LocalDateTime.now());
                cursorEntity = feedCursorRepository.save(cursorEntity);
            }
        }

        if (batchSuccessful && feedResponse.nextCursor() != null
            && feedResponse.nextCursor() > cursorEntity.getCursorValue()) {
            cursorEntity.setCursorValue(feedResponse.nextCursor());
            cursorEntity.setUpdatedAt(LocalDateTime.now());
            feedCursorRepository.save(cursorEntity);
        }
    }
}
