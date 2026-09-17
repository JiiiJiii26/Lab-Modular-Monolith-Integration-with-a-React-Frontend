package edu.cit.pena.shop;

/**
 * Request representation of a line item in an order placement.
 */
public record OrderItemRequest(
        String productId,
        int quantity
) {}
