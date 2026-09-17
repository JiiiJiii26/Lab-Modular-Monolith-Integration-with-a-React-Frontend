package edu.cit.pena.shared.events;

import java.util.List;

/**
 * Domain event published when an existing order is cancelled and its items restocked.
 */
public record OrderCancelledEvent(
        Long orderId,
        List<OrderItemDto> items
) {}
