package edu.cit.pena.channel;

/**
 * Public enum representing the order status on the Tiangge channel.
 */
public enum TianggeOrderStatus {
    AWAITING_DECISION,
    ACCEPTED,
    REJECTED,
    BACKORDERED,
    CANCELLED,
    CANCELLATION_PENDING,
    CANCELLED_BY_CUSTOMER
}
