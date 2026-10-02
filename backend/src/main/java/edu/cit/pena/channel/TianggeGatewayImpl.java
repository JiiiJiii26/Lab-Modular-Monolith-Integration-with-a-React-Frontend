package edu.cit.pena.channel;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * PACKAGE-PRIVATE implementation of TianggeGateway.
 * Delegates to TianggeInstanceManager, TianggeListingService, TianggeStockPublisher.
 * goLive() is a no-op here because TianggeInstanceManager implements ApplicationRunner
 * and handles startup automatically.
 */
@Service
class TianggeGatewayImpl implements TianggeGateway {

    private final TianggeInstanceManager instanceManager;
    private final TianggeListingService listingService;
    private final TianggeStockPublisher stockPublisher;

    TianggeGatewayImpl(
            TianggeInstanceManager instanceManager,
            TianggeListingService listingService,
            TianggeStockPublisher stockPublisher
    ) {
        this.instanceManager = instanceManager;
        this.listingService = listingService;
        this.stockPublisher = stockPublisher;
    }

    /**
     * Called at startup. TianggeInstanceManager.run() already handles initialization
     * via ApplicationRunner; this method is provided for explicit programmatic calls.
     */
    @Override
    public void goLive() {
        // No-op: ApplicationRunner.run() already handled startup registration and publishing.
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
}
