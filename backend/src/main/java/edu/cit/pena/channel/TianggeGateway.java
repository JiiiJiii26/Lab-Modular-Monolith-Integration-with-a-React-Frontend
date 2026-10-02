package edu.cit.pena.channel;

import java.util.List;
import java.util.Map;

/**
 * Public gateway interface — the ONLY entry point into the channel module
 * for the rest of the application.
 */
public interface TianggeGateway {

    void goLive();

    void publishListings(List<Listing> listings);

    void publishStock(Map<String, Integer> stockBySellerSku);

    String instanceId();

    /**
     * Fetch a batch of feed events from Tiangge.
     *
     * @param after the cursor to fetch after (exclusive)
     * @param limit max events to return (1..50)
     * @return TianggeFeedPage with events + next cursor
     */
    TianggeFeedPage fetchFeed(long after, int limit);

    /**
     * Report a decision for a Tiangge order.
     *
     * @param orderId     Tiangge order id
     * @param decision    ACCEPTED / REJECTED / BACKORDERED
     * @param shopOrderId my local order id (as String)
     * @param reason      optional up to 200 chars
     */
    void reportDecision(String orderId, TianggeDecision decision, String shopOrderId, String reason);

    /**
     * Confirm that a customer cancellation was handled (stock restocked).
     *
     * @param orderId Tiangge order id
     */
    void confirmCancellation(String orderId);
}