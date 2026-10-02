package edu.cit.pena.channel;

import java.util.List;

/**
 * PACKAGE-PRIVATE translator: converts raw Tiangge JSON DTOs to domain types.
 */
class TianggeTranslator {

    private TianggeTranslator() {}

    static TianggeEvent toEvent(TianggeDtos.FeedEventRaw raw) {
        if (raw == null) return null;
        List<TianggeLine> lines = raw.lines() == null ? List.of() :
                raw.lines().stream()
                        .map(l -> new TianggeLine(l.sellerSku(), l.qty()))
                        .toList();
        return new TianggeEvent(
                raw.seq(),
                raw.eventId(),
                raw.type(),
                raw.orderId(),
                raw.placedAt(),
                raw.decisionDeadline(),
                raw.cancelledAt(),
                raw.confirmDeadline(),
                lines,
                raw.buyerName(),
                raw.buyerCity()
        );
    }

    static TianggeOrderStatus toOrderStatus(String raw) {
        if (raw == null) return TianggeOrderStatus.AWAITING_DECISION;
        try {
            return TianggeOrderStatus.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return TianggeOrderStatus.AWAITING_DECISION;
        }
    }
}
