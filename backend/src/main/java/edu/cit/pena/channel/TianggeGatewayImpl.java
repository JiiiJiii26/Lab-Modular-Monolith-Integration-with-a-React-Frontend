package edu.cit.pena.channel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import edu.cit.pena.inventory.InventoryItem;
import edu.cit.pena.inventory.InventoryService;

/**
 * PACKAGE-PRIVATE implementation of TianggeGateway.
 */
@Service
class TianggeGatewayImpl implements TianggeGateway {

    private final TianggeInstanceManager instanceManager;
    private final TianggeListingService listingService;
    private final TianggeStockPublisher stockPublisher;
    private final TianggeFeedClient feedClient;
    private final InventoryService inventoryService;

    TianggeGatewayImpl(
            TianggeInstanceManager instanceManager,
            TianggeListingService listingService,
            TianggeStockPublisher stockPublisher,
            TianggeFeedClient feedClient,
            InventoryService inventoryService
    ) {
        this.instanceManager = instanceManager;
        this.listingService = listingService;
        this.stockPublisher = stockPublisher;
        this.feedClient = feedClient;
        this.inventoryService = inventoryService;
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
    public boolean publishStock(Map<String, Integer> stockBySellerSku) {
        return stockPublisher.publishStock(stockBySellerSku);
    }

    @Override
    public boolean publishCurrentStock() {
        Map<String, Integer> stock = new HashMap<>();
        for (InventoryItem item : inventoryService.getAllItems()) {
            stock.put(item.getProductId(), item.getStock());
        }
        return stockPublisher.publishStock(stock);
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
    public boolean reportDecision(String orderId, TianggeDecision decision, String shopOrderId, String reason) {
        return feedClient.reportDecision(orderId, decision, shopOrderId, reason);
    }

    @Override
    public void confirmCancellation(String orderId) {
        feedClient.confirmCancellation(orderId);
    }
}