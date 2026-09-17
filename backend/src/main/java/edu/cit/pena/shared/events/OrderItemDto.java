package edu.cit.pena.shared.events;

/**
 * Data transfer representation of an order line item within domain events.
 */
public record OrderItemDto(
        String productId,
        int quantity
) {}
