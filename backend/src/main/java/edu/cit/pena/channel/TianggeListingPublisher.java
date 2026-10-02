package edu.cit.pena.channel;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Publishes product listings to Tiangge marketplace on startup.
 */
@Component
class TianggeListingPublisher {

    private static final Logger log = LoggerFactory.getLogger(TianggeListingPublisher.class);

    private final TianggeClient httpClient;

    public TianggeListingPublisher(TianggeClient httpClient) {
        this.httpClient = httpClient;
    }

    public void publishListings() {
        List<Models.ListingItem> listings = List.of(
            new Models.ListingItem("P100", "Wireless Mouse", "GSF-1861"),
            new Models.ListingItem("P200", "Mechanical Keyboard", "GSF-2186"),
            new Models.ListingItem("P300", "USB-C Hub", "GSF-4040")
        );

        log.info("Publishing {} catalog listings to Tiangge marketplace...", listings.size());
        httpClient.publishListings(listings);
        log.info("Listings successfully published to Tiangge: P100 (GSF-1861), P200 (GSF-2186), P300 (GSF-4040)");
    }
}
