package edu.cit.pena.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PACKAGE-PRIVATE HTTP client for Tiangge feed + decision endpoints.
 */
@Component
class TianggeFeedClient {

    private static final Logger log = LoggerFactory.getLogger(TianggeFeedClient.class);

    private final TianggeHttpClient httpClient;

    @Value("${channel.tiangge.feed-limit:20}")
    private int defaultLimit;

    TianggeFeedClient(TianggeHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    TianggeFeedPage fetchFeed(long after, int limit) {
        int n = Math.max(1, Math.min(50, limit <= 0 ? defaultLimit : limit));
        String path = "/feed?after=" + after + "&limit=" + n;
        try {
            var response = httpClient.get(path);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Tiangge fetchFeed HTTP {} body={}", response.statusCode(), response.body());
                return new TianggeFeedPage(List.of(), after);
            }
            TianggeDtos.FeedResponse parsed = httpClient.parseJson(response.body(), TianggeDtos.FeedResponse.class);
            if (parsed == null || parsed.events() == null) {
                return new TianggeFeedPage(List.of(), after);
            }
            List<TianggeEvent> events = parsed.events().stream()
                    .map(TianggeTranslator::toEvent)
                    .toList();
            long nextCursor = parsed.nextCursor() != null ? parsed.nextCursor() : after;
            return new TianggeFeedPage(events, nextCursor);
        } catch (Exception e) {
            log.warn("Tiangge fetchFeed error: {}", e.getMessage());
            return new TianggeFeedPage(List.of(), after);
        }
    }

    void reportDecision(String orderId, TianggeDecision decision, String shopOrderId, String reason) {
        String path = "/orders/" + orderId + "/decision";
        Map<String, Object> body = new HashMap<>();
        body.put("decision", decision.name());
        body.put("shopOrderId", shopOrderId);
        if (reason != null) body.put("reason", reason);
        try {
            var response = httpClient.post(path, body);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Tiangge decision HTTP {} for {} body={}", response.statusCode(), orderId, response.body());
            } else {
                log.info("Reported {} for Tiangge order {} -> shopOrder {}", decision, orderId, shopOrderId);
            }
        } catch (Exception e) {
            log.warn("Tiangge decision error for {}: {}", orderId, e.getMessage());
        }
    }

    void confirmCancellation(String orderId) {
        String path = "/orders/" + orderId + "/cancellation";
        Map<String, Object> body = new HashMap<>();
        body.put("restocked", true);
        try {
            var response = httpClient.post(path, body);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Tiangge cancel HTTP {} for {} body={}", response.statusCode(), orderId, response.body());
            } else {
                log.info("Confirmed cancellation to Tiangge for {}", orderId);
            }
        } catch (Exception e) {
            log.warn("Tiangge cancel error for {}: {}", orderId, e.getMessage());
        }
    }
}