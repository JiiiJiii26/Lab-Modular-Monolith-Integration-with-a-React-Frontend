package edu.cit.pena.channel;

import java.util.List;

/**
 * Public record representing one feed event from the Tiangge channel.
 */
public record TianggeEvent(
        long seq,
        String eventId,
        String type,            // ORDER_PLACED, ORDER_CANCELLED
        String orderId,         // Tiangge order id
        String placedAt,
        String decisionDeadline,
        String cancelledAt,
        String confirmDeadline,
        List<TianggeLine> lines,
        String buyerName,
        String buyerCity
) {}
