package edu.cit.pena.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Package-private orchestrator for application startup and Tiangge onboarding.
 * Publishes heartbeat, listings, and initial stock to establish the shop live.
 */
@Component
class ChannelLifecycleManager implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ChannelLifecycleManager.class);

    private final String apiKey;
    private final ChannelGatewayImpl channelGateway;
    private final TianggeHeartbeatService heartbeatService;
    private final TianggeListingPublisher listingPublisher;
    private final TianggeStockSyncListener stockSyncListener;
    private final TianggeOrderProcessor orderProcessor;

    public ChannelLifecycleManager(
            @Value("${tiangge.api-key:${LS_API_KEY:}}") String apiKey,
            ChannelGatewayImpl channelGateway,
            TianggeHeartbeatService heartbeatService,
            TianggeListingPublisher listingPublisher,
            TianggeStockSyncListener stockSyncListener,
            TianggeOrderProcessor orderProcessor) {
        this.apiKey = apiKey;
        this.channelGateway = channelGateway;
        this.heartbeatService = heartbeatService;
        this.listingPublisher = listingPublisher;
        this.stockSyncListener = stockSyncListener;
        this.orderProcessor = orderProcessor;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("==================================================================");
        log.info("App starting with Instance ID: {}", channelGateway.getInstanceId());
        log.info("==================================================================");

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Tiangge/LegacySupply API key (LS_API_KEY) is not set.");
            log.warn("Please set the LS_API_KEY environment variable to go live on Tiangge.");
            return;
        }

        try {
            // Task 1: Identify and send initial heartbeat
            log.info("Task 1: Sending initial heartbeat to Tiangge...");
            if (!heartbeatService.sendHeartbeat()) {
                throw new IllegalStateException("Initial Tiangge heartbeat failed");
            }

            // Task 2: Publish product listings
            log.info("Task 2: Publishing product listings to Tiangge...");
            listingPublisher.publishListings();

            // Task 2/3: Publish initial inventory stock levels
            log.info("Task 2/3: Synchronizing baseline inventory stock to Tiangge...");
            stockSyncListener.publishCurrentStock();

            channelGateway.setLive(true);
            log.info("Channel is now LIVE on Tiangge marketplace with Instance ID: {}", channelGateway.getInstanceId());

            // Task 6: Resolve any pending backorders now that channel is live
            orderProcessor.resolvePendingBackorders();
        } catch (Exception ex) {
            log.error("Failed to initialize Tiangge channel on startup: {}", ex.getMessage(), ex);
        }
    }
}
