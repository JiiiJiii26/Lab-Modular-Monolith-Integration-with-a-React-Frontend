package edu.cit.pena.channel;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * PACKAGE-PRIVATE JSON DTOs used internally by TianggeHttpClient.
 */
class TianggeDtos {

    // ── Heartbeat ─────────────────────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record HeartbeatRequest(
            @JsonProperty("appName") String appName,
            @JsonProperty("startedAt") String startedAt,
            @JsonProperty("uptimeSeconds") long uptimeSeconds
    ) {}

    // ── Listings ──────────────────────────────────────────────────────────────

    record ListingItem(
            @JsonProperty("sellerSku") String sellerSku,
            @JsonProperty("title") String title,
            @JsonProperty("supplierSku") String supplierSku
    ) {}

    record PublishListingsRequest(
            @JsonProperty("listings") List<ListingItem> listings
    ) {}

    // ── Stock ─────────────────────────────────────────────────────────────────

    record StockEntry(
            @JsonProperty("sellerSku") String sellerSku,
            @JsonProperty("qty") int qty
    ) {}

    record PublishStockRequest(
            @JsonProperty("stock") List<StockEntry> stock
    ) {}

    // ── Feed response ─────────────────────────────────────────────────────────

    record FeedResponse(
            @JsonProperty("events") List<FeedEventRaw> events,
            @JsonProperty("nextCursor") Long nextCursor
    ) {}

    record FeedEventRaw(
            @JsonProperty("seq") long seq,
            @JsonProperty("eventId") String eventId,
            @JsonProperty("type") String type,
            @JsonProperty("orderId") String orderId,
            @JsonProperty("placedAt") String placedAt,
            @JsonProperty("decisionDeadline") String decisionDeadline,
            @JsonProperty("cancelledAt") String cancelledAt,
            @JsonProperty("confirmDeadline") String confirmDeadline,
            @JsonProperty("lines") List<FeedLineRaw> lines,
            @JsonProperty("buyerName") String buyerName,
            @JsonProperty("buyerCity") String buyerCity
    ) {}

    record FeedLineRaw(
            @JsonProperty("sellerSku") String sellerSku,
            @JsonProperty("qty") int qty
    ) {}
}
