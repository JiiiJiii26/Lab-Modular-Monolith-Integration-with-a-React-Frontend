package edu.cit.pena.shop;

/**
 * Request DTO for creating an order.
 */
public record OrderRequest(
        String productId,
        int quantity
) {}
