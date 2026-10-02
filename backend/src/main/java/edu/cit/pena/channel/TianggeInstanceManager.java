package edu.cit.pena.channel;

import edu.cit.pena.inventory.InventoryService;
import edu.cit.pena.supplier.SupplierGateway;
import edu.cit.pena.supplier.SupplierProductMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PACKAGE-PRIVATE instance manager.
 * Generates UUID on startup, exposes it, sends heartbeats, and publishes initial listings/stock.
 */
@Component
class TianggeInstanceManager implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TianggeInstanceManager.class);

    private final String appName;
    private final TianggeHttpClient httpClient;
    private final TianggeListingService listingService;
    private final TianggeStockPublisher stockPublisher;
    private final SupplierGateway supplierGateway;
    private final InventoryService inventoryService;

    private volatile String instanceId;
    private volatile Instant startedAt;

    TianggeInstanceManager(
            @Value("${channel.tiangge.app-name}") String appName,
            TianggeHttpClient httpClient,
            TianggeListingService listingService,
            TianggeStockPublisher stockPublisher,
            SupplierGateway supplierGateway,
            InventoryService inventoryService
    ) {
        this.appName = appName;
        this.httpClient = httpClient;
        this.listingService = listingService;
        this.stockPublisher = stockPublisher;
        this.supplierGateway = supplierGateway;
        this.inventoryService = inventoryService;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 1. Generate instance ID
        this.instanceId = UUID.randomUUID().toString();
        this.startedAt = Instant.now();
        log.info("Tiangge instance started: {}", instanceId);

        // 2. Send first heartbeat immediately
        sendHeartbeat();

        // 3. Publish initial listings
        List<String> productIds = List.of("P100", "P200", "P300");
        Map<String, String> productTitles = Map.of(
                "P100", "Wireless Mouse",
                "P200", "Mechanical Keyboard",
                "P300", "USB-C Hub"
        );

        List<Listing> listings = productIds.stream()
                .map(productId -> {
                    SupplierProductMapping mapping = supplierGateway.mappingFor(productId);
                    String supplierSku = (mapping != null) ? mapping.supplierSku() : productId;
                    String title = productTitles.getOrDefault(productId, productId);
                    return new Listing(productId, title, supplierSku);
                })
                .toList();

        listingService.publishListings(listings);

        // 4. Publish initial stock
        Map<String, Integer> stockMap = new java.util.LinkedHashMap<>();
        for (String productId : productIds) {
            var item = inventoryService.getItem(productId);
            if (item != null) {
                stockMap.put(productId, item.getStock());
            }
        }
        stockPublisher.publishStock(stockMap);
    }

    /**
     * Scheduled heartbeat every N seconds (configured by channel.tiangge.heartbeat-seconds).
     */
    @Scheduled(fixedDelayString = "${channel.tiangge.heartbeat-seconds:30}000")
    void sendHeartbeat() {
        try {
            long uptimeSeconds = startedAt != null
                    ? java.time.Duration.between(startedAt, Instant.now()).getSeconds()
                    : 0L;
            String startedAtStr = startedAt != null ? startedAt.toString() : Instant.now().toString();

            var body = new TianggeDtos.HeartbeatRequest(appName, startedAtStr, uptimeSeconds);
            var response = httpClient.post("/instances/heartbeat", body);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Tiangge heartbeat failed: HTTP {}", response.statusCode());
            }
        } catch (Exception e) {
            log.warn("Tiangge heartbeat error: {}", e.getMessage());
        }
    }

    /** Returns the current instance ID. May be null before startup completes. */
    String getInstanceId() {
        return instanceId;
    }
}
