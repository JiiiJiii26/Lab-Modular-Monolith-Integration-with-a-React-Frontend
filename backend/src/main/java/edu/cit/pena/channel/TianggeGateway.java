package edu.cit.pena.channel;

import java.util.List;
import java.util.Map;

/**
 * Public gateway interface — the ONLY entry point into the channel module
 * for the rest of the application.
 */
public interface TianggeGateway {

    /**
     * Called at startup. Registers the instance and publishes initial listings.
     */
    void goLive();

    /**
     * Publish a listing set to Tiangge. Replaces all previous listings.
     *
     * @param listings list of listings to publish
     */
    void publishListings(List<Listing> listings);

    /**
     * Publish current stock for one or more seller SKUs.
     *
     * @param stockBySellerSku map of sellerSku to available stock quantity
     */
    void publishStock(Map<String, Integer> stockBySellerSku);

    /**
     * Current instance ID (UUID) — same value across all outbound calls.
     *
     * @return the instance ID string
     */
    String instanceId();
}
