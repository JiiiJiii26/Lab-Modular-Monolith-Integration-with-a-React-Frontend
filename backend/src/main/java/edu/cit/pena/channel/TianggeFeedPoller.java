package edu.cit.pena.channel;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import edu.cit.pena.shop.OrderItemRequest;
import edu.cit.pena.shop.OrderRequest;
import edu.cit.pena.shop.OrderResponse;
import edu.cit.pena.shop.OrderService;

/**
 * PACKAGE-PRIVATE feed poller.
 * Runs every N seconds, reads new events from Tiangge, and processes each once:
 *  - ORDER_PLACED    -> placeOrder() in Order module, then report ACCEPTED/REJECTED
 *  - ORDER_CANCELLED -> cancelOrder(), then confirmCancellation() to Tiangge
 * The cursor is persisted in tiangge_feed_cursor. Events are deduped by eventId
 * in tiangge_events_processed, so replayed events never double-process.
 */
@Component
class TianggeFeedPoller {

    private static final Logger log = LoggerFactory.getLogger(TianggeFeedPoller.class);

    private final TianggeGateway gateway;
    private final TianggeFeedCursorRepository cursorRepo;
    private final TianggeEventProcessedRepository processedRepo;
    private final TianggeOrderMapRepository orderMapRepo;
    private final OrderService orderService;

    @Value("${channel.tiangge.feed-limit:20}")
    private int feedLimit;

    TianggeFeedPoller(
            TianggeGateway gateway,
            TianggeFeedCursorRepository cursorRepo,
            TianggeEventProcessedRepository processedRepo,
            TianggeOrderMapRepository orderMapRepo,
            OrderService orderService
    ) {
        this.gateway = gateway;
        this.cursorRepo = cursorRepo;
        this.processedRepo = processedRepo;
        this.orderMapRepo = orderMapRepo;
        this.orderService = orderService;
    }

    @Scheduled(fixedDelayString = "${channel.tiangge.feed-poll-seconds:5}000")
    @Transactional
    void tick() {
        try {
            TianggeFeedCursor cursor = cursorRepo.findById(1).orElse(null);
            if (cursor == null) {
                log.warn("Feed cursor row (id=1) missing - initializing to 0");
                cursorRepo.save(new TianggeFeedCursor(1, 0, Instant.now()));
                return;
            }

            long after = cursor.getLastCursor();
            TianggeFeedPage page = gateway.fetchFeed(after, feedLimit);
            if (page.events() == null || page.events().isEmpty()) {
                return;
            }

            log.info("Feed: got {} event(s) after cursor {}", page.events().size(), after);

            boolean allOk = true;
            for (TianggeEvent event : page.events()) {
                try {
                    processEvent(event);
                } catch (Exception e) {
                    log.warn("Failed to process event {} (seq {}): {}", event.eventId(), event.seq(), e.getMessage());
                    allOk = false;
                    break;
                }
            }

            if (allOk && page.nextCursor() > after) {
                cursor.setLastCursor(page.nextCursor());
                cursorRepo.save(cursor);
                log.debug("Feed cursor advanced to {}", page.nextCursor());
            } else if (!allOk) {
                log.warn("Stopping feed batch early - cursor stays at {} for retry", after);
            }
        } catch (Exception e) {
            log.warn("Feed tick error: {}", e.getMessage());
        }
    }

    private void processEvent(TianggeEvent event) {
        if (event.eventId() == null) {
            log.warn("Event without eventId - skipping seq {}", event.seq());
            return;
        }
        if (processedRepo.existsById(event.eventId())) {
            log.debug("Event {} already processed - skipping", event.eventId());
            return;
        }
        processedRepo.save(new TianggeEventProcessed(event.eventId()));
        switch (event.type()) {
            case "ORDER_PLACED" -> handlePlaced(event);
            case "ORDER_CANCELLED" -> handleCancelled(event);
            default -> log.info("Ignoring unsupported event type: {}", event.type());
        }
    }

    private void handlePlaced(TianggeEvent event) {
        if (event.lines() == null || event.lines().isEmpty()) {
            log.warn("ORDER_PLACED {} has no lines - rejecting", event.orderId());
            gateway.reportDecision(event.orderId(), TianggeDecision.REJECTED, "0", "Empty order lines");
            return;
        }

        List<OrderItemRequest> items = event.lines().stream()
                .map(l -> new OrderItemRequest(l.sellerSku(), l.qty()))
                .toList();

        OrderResponse response = orderService.placeOrder(new OrderRequest(items));
        Long shopOrderId = response.orderId();

        TianggeDecision decision = "CONFIRMED".equalsIgnoreCase(response.status())
                ? TianggeDecision.ACCEPTED
                : TianggeDecision.REJECTED;

        Instant deadline = parseInstant(event.decisionDeadline());
        orderMapRepo.save(new TianggeOrderMap(
                event.orderId(),
                shopOrderId,
                decision.name(),
                deadline
        ));

        gateway.reportDecision(
                event.orderId(),
                decision,
                String.valueOf(shopOrderId),
                decision == TianggeDecision.REJECTED ? response.reason() : null
        );
    }

    private void handleCancelled(TianggeEvent event) {
        TianggeOrderMap map = orderMapRepo.findById(event.orderId()).orElse(null);
        if (map == null) {
            log.warn("ORDER_CANCELLED for {} but no local mapping - confirming anyway", event.orderId());
            gateway.confirmCancellation(event.orderId());
            return;
        }

        try {
            orderService.cancelOrder(map.getShopOrderId());
        } catch (Exception e) {
            log.warn("Cancel failed for shop order {} (Tiangge {}): {}",
                    map.getShopOrderId(), event.orderId(), e.getMessage());
        }

        map.setStatus("CANCELLED_BY_CUSTOMER");
        orderMapRepo.save(map);

        gateway.confirmCancellation(event.orderId());
    }

    private Instant parseInstant(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return Instant.parse(iso);
        } catch (Exception e) {
            return null;
        }
    }
}