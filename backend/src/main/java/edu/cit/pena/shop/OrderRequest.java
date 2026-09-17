package edu.cit.pena.shop;

import java.util.List;

/**
 * Request DTO for creating orders.
 * Supports multi-item format: { "items": [ { "productId": "...", "quantity": N }, ... ] }
 * and maintains backward-compatibility with single-item requests.
 */
public record OrderRequest(
        List<OrderItemRequest> items,
        String productId,
        Integer quantity
) {
    public OrderRequest(String productId, Integer quantity) {
        this(
                (productId != null && quantity != null) ? List.of(new OrderItemRequest(productId, quantity)) : List.of(),
                productId,
                quantity
        );
    }

    public OrderRequest(List<OrderItemRequest> items) {
        this(items, null, null);
    }

    public List<OrderItemRequest> getEffectiveItems() {
        if (items != null && !items.isEmpty()) {
            return items;
        }
        if (productId != null && !productId.isBlank() && quantity != null) {
            return List.of(new OrderItemRequest(productId, quantity));
        }
        return List.of();
    }
}
