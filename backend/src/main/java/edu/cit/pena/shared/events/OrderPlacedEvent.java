package edu.cit.pena.shared.events;

import java.util.List;

/**
 * Domain event published when an order is successfully placed and confirmed.
 */
public record OrderPlacedEvent(
        Long orderId,
        List<OrderItemDto> items
) {}
