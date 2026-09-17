package edu.cit.pena.shop;

/**
 * Outcome status of an individual item in an order response ("RESERVED" or "REJECTED").
 */
public record OrderItemOutcome(
        String productId,
        String outcome
) {}
