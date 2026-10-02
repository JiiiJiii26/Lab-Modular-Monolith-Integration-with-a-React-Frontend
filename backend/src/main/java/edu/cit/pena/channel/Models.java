package edu.cit.pena.channel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Package-private wire models and DTOs for the Tiangge API.
 */
class Models {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record HeartbeatRequest(String appName, String startedAt, long uptimeSeconds) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HeartbeatResponse(String serverTime, Long nextHeartbeatSeconds) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ListingItem(String sellerSku, String title, String supplierSku) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record StockItem(String sellerSku, int available) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FeedResponse(List<FeedEvent> events, Long nextCursor) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FeedEvent(
            long seq,
            String eventId,
            String type,
            String orderId,
            String placedAt,
            String decisionDeadline,
            String cancelledAt,
            String confirmDeadline,
            List<FeedLine> lines,
            FeedBuyer buyer
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FeedLine(String sellerSku, int qty) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FeedBuyer(String name, String city) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DecisionRequest(String decision, String shopOrderId, String reason) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ResolutionRequest(String status) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CancellationRequest(boolean restocked) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorResponse(String error, String message) {}
}
