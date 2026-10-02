package edu.cit.pena.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * PACKAGE-PRIVATE service that publishes listings to Tiangge.
 */
@Component
class TianggeListingService {

    private static final Logger log = LoggerFactory.getLogger(TianggeListingService.class);

    private final TianggeHttpClient httpClient;

    TianggeListingService(TianggeHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    void publishListings(List<Listing> listings) {
        try {
            var items = listings.stream()
                    .map(l -> new TianggeDtos.ListingItem(l.sellerSku(), l.title(), l.supplierSku()))
                    .toList();
            var body = items;
            var response = httpClient.put("/listings", body);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Published {} listings to Tiangge", listings.size());
            } else {
                log.warn("Tiangge publishListings failed: HTTP {} body={}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("Tiangge publishListings error: {}", e.getMessage());
        }
    }
}
