package edu.cit.pena.shop;

/**
 * Result object for order cancellation attempts.
 */
public record CancelOrderResult(
        Status status,
        OrderDetailsDto order,
        String message
) {
    public enum Status {
        SUCCESS,
        NOT_FOUND,
        ALREADY_CANCELLED
    }

    public static CancelOrderResult success(OrderDetailsDto order) {
        return new CancelOrderResult(Status.SUCCESS, order, "Order cancelled successfully.");
    }

    public static CancelOrderResult notFound(String message) {
        return new CancelOrderResult(Status.NOT_FOUND, null, message);
    }

    public static CancelOrderResult alreadyCancelled(String message) {
        return new CancelOrderResult(Status.ALREADY_CANCELLED, null, message);
    }
}
