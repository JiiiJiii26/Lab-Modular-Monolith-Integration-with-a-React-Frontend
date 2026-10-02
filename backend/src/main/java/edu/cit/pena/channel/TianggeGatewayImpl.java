package edu.cit.pena.channel;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * PACKAGE-PRIVATE implementation of TianggeGateway.
 */
@Service
class TianggeGatewayImpl implements TianggeGateway {

    private final TianggeInstanceManager instanceManager;
    private final TianggeListingService listingService;
    private final TianggeStockPublisher stockPublisher;
    private final TianggeFeedClient feedClient;

    TianggeGatewayImpl(
            TianggeInstanceManager instanceManager,
            TianggeListingService listingService,
            TianggeStockPublisher stockPublisher,
            TianggeFeedClient feedClient
    ) {
        this.instanceManager = instanceManager;
        this.listingService = listingService;
        this.stockPublisher = stockPublisher;
        this.feedClient = feedClient;
    }

    @Override
    public void goLive() {
        // No-op: ApplicationRunner in TianggeInstanceManager handles startup.
    }

    @Override
    public void publishListings(List<Listing> listings) {
        listingService.publishListings(listings);
    }

    @Override
    public void publishStock(Map<String, Integer> stockBySellerSku) {
        stockPublisher.publishStock(stockBySellerSku);
    }

    @Override
    public String instanceId() {
        return instanceManager.getInstanceId();
    }

    @Override
    public TianggeFeedPage fetchFeed(long after, int limit) {
        return feedClient.fetchFeed(after, limit);
    }

    @Override
    public void reportDecision(String orderId, TianggeDecision decision, String shopOrderId, String reason) {
        feedClient.reportDecision(orderId, decision, shopOrderId, reason);
    }

    @Override
    public void confirmCancellation(String orderId) {
        feedClient.confirmCancellation(orderId);
    }
}