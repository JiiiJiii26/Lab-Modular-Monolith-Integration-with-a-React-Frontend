package edu.cit.pena.shared.events;

import java.util.List;

/**
 * Domain event published when an order placement is rejected.
 */
public record OrderRejectedEvent(
        Long orderId,
        String reason,
        List<OrderItemDto> items
) {}
