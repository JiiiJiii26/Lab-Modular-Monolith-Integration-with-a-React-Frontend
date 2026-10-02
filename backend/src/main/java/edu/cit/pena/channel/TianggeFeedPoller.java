package edu.cit.pena.channel;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
 * via TianggeEventDeduper, so replayed events never double-process.
 */
@Component
class TianggeFeedPoller {

    private static final Logger log = LoggerFactory.getLogger(TianggeFeedPoller.class);

    private final TianggeGateway gateway;
    private final TianggeFeedCursorRepository cursorRepo;
    private final TianggeEventDeduper deduper;
    private final TianggeOrderMapRepository orderMapRepo;
    private final OrderService orderService;

    @Value("${channel.tiangge.feed-limit:20}")
    private int feedLimit;

    TianggeFeedPoller(
            TianggeGateway gateway,
            TianggeFeedCursorRepository cursorRepo,
            TianggeEventDeduper deduper,
            TianggeOrderMapRepository orderMapRepo,
            OrderService orderService
    ) {
        this.gateway = gateway;
        this.cursorRepo = cursorRepo;
        this.deduper = deduper;
        this.orderMapRepo = orderMapRepo;
        this.orderService = orderService;
    }

    @Scheduled(fixedDelayString = "${channel.tiangge.feed-poll-seconds:5}000")
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
        if (deduper.isProcessed(event.eventId())) {
            log.info("Event {} already processed - skipping", event.eventId());
            return;
        }
        switch (event.type()) {
            case "ORDER_PLACED" -> handlePlaced(event);
            case "ORDER_CANCELLED" -> handleCancelled(event);
            default -> log.info("Ignoring unsupported event type: {}", event.type());
        }
        deduper.markProcessed(event.eventId());
    }

    private void handlePlaced(TianggeEvent event) {
        TianggeOrderMap existingMap = orderMapRepo.findById(event.orderId()).orElse(null);
        if (existingMap != null) {
            TianggeDecision existingDecision = TianggeDecision.valueOf(existingMap.getStatus());
            reportDecisionAndPublishStock(event.orderId(), existingDecision, existingMap.getShopOrderId());
            return;
        }

        if (event.lines() == null || event.lines().isEmpty()) {
            log.warn("ORDER_PLACED {} has no lines - rejecting", event.orderId());
            if (!gateway.reportDecision(
                    event.orderId(),
                    TianggeDecision.REJECTED,
                    "0",
                    "Empty order lines"
            )) {
                throw new IllegalStateException("Failed to report empty-order rejection");
            }
            return;
        }

        List<OrderItemRequest> items = event.lines().stream()
                .map(l -> new OrderItemRequest(l.sellerSku(), l.qty()))
                .toList();

        OrderResponse response = TianggeStockSyncListener.suppressForCurrentThread(
            () -> orderService.placeOrder(new OrderRequest(items))
        );
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

        reportDecisionAndPublishStock(
                event.orderId(),
                decision,
            shopOrderId,
            decision == TianggeDecision.REJECTED ? response.reason() : null
        );
    }

        private void reportDecisionAndPublishStock(
            String tianggeOrderId,
            TianggeDecision decision,
            long shopOrderId
        ) {
        reportDecisionAndPublishStock(tianggeOrderId, decision, shopOrderId, null);
        }

        private void reportDecisionAndPublishStock(
            String tianggeOrderId,
            TianggeDecision decision,
            long shopOrderId,
            String reason
        ) {
        if (!gateway.reportDecision(
            tianggeOrderId,
            decision,
            String.valueOf(shopOrderId),
            reason
        )) {
            throw new IllegalStateException("Failed to report decision for " + tianggeOrderId);
        }

        if (decision == TianggeDecision.ACCEPTED && !gateway.publishCurrentStock()) {
            throw new IllegalStateException(
                "Decision reported but stock publication failed for " + tianggeOrderId
            );
        }
        }

    private void handleCancelled(TianggeEvent event) {
        TianggeOrderMap map = orderMapRepo.findById(event.orderId()).orElse(null);
        if (map == null) {
            log.warn("ORDER_CANCELLED for {} but no local mapping - confirming anyway", event.orderId());
            if (!gateway.confirmCancellation(event.orderId())) {
                throw new IllegalStateException("Failed to confirm cancellation for " + event.orderId());
            }
            return;
        }

        if (!"CANCELLED_BY_CUSTOMER".equals(map.getStatus())) {
            try {
                TianggeStockSyncListener.suppressForCurrentThread(
                        () -> orderService.cancelOrder(map.getShopOrderId())
                );
            } catch (Exception e) {
                log.warn("Cancel failed for shop order {} (Tiangge {}): {}",
                        map.getShopOrderId(), event.orderId(), e.getMessage());
                throw e;
            }

            map.setStatus("CANCELLED_BY_CUSTOMER");
            orderMapRepo.save(map);
        }

        if (!gateway.confirmCancellation(event.orderId())) {
            throw new IllegalStateException("Failed to confirm cancellation for " + event.orderId());
        }

        if (!gateway.publishCurrentStock()) {
            throw new IllegalStateException("Failed to publish stock after cancellation " + event.orderId());
        }
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