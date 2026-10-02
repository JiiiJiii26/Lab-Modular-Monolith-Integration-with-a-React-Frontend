package edu.cit.pena.channel;

import java.util.List;

/**
 * Package-private client contract for Tiangge Marketplace.
 */
interface TianggeClient {

    Models.HeartbeatResponse sendHeartbeat(Models.HeartbeatRequest request);

    void publishListings(List<Models.ListingItem> listings);

    void publishStock(List<Models.StockItem> stockItems);

    Models.FeedResponse getFeed(long after, int limit);

    void sendDecision(String orderId, Models.DecisionRequest request);

    void sendResolution(String orderId, Models.ResolutionRequest request);

    void sendCancellationConfirmation(String orderId, Models.CancellationRequest request);
}
