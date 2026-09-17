package edu.cit.pena.shop;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * DTO representing an order with its line items for order history listings.
 */
public record OrderDetailsDto(
        Long orderId,
        String status,
        String reason,
        OffsetDateTime createdAt,
        List<ItemDetail> items,
        int totalQuantity
) {
    public record ItemDetail(
            Long orderItemId,
            String productId,
            int quantity
    ) {}

    public static OrderDetailsDto from(Order order) {
        List<ItemDetail> itemDetails = order.getItems() != null
                ? order.getItems().stream()
                .map(i -> new ItemDetail(i.getOrderItemId(), i.getProductId(), i.getQuantity()))
                .toList()
                : List.of();

        int totalQty = itemDetails.stream().mapToInt(ItemDetail::quantity).sum();

        return new OrderDetailsDto(
                order.getOrderId(),
                order.getStatus(),
                order.getReason(),
                order.getCreatedAt(),
                itemDetails,
                totalQty
        );
    }
}
