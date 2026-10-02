package edu.cit.pena.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

/**
 * Package-private HTTP client for Tiangge Marketplace API.
 */
@Component
class TianggeHttpClient implements TianggeClient {

    private static final Logger log = LoggerFactory.getLogger(TianggeHttpClient.class);

    private final String baseUrl;
    private final String clientId;
    private final String apiKey;
    private final ChannelGateway channelGateway;
    private final RestClient restClient;

    public TianggeHttpClient(
            @Value("${tiangge.base-url:https://legacysupply.onrender.com/tiangge/v1}") String baseUrl,
            @Value("${tiangge.client-id:${LS_CLIENT_ID:23-5396-310}}") String clientId,
            @Value("${tiangge.api-key:${LS_API_KEY:}}") String apiKey,
            ChannelGateway channelGateway) {
        this.baseUrl = baseUrl;
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.channelGateway = channelGateway;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    private RestClient.RequestBodySpec preparePost(String uri, Object... uriVariables) {
        var spec = restClient.post()
                .uri(uri, uriVariables)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header("X-Client-Id", clientId)
                .header("X-Client-Instance", channelGateway.getInstanceId());

        if (apiKey != null && !apiKey.isBlank()) {
            spec.header("Authorization", "Bearer " + apiKey.trim());
        }
        return spec;
    }

    private RestClient.RequestBodySpec preparePut(String uri, Object... uriVariables) {
        var spec = restClient.put()
                .uri(uri, uriVariables)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header("X-Client-Id", clientId)
                .header("X-Client-Instance", channelGateway.getInstanceId());

        if (apiKey != null && !apiKey.isBlank()) {
            spec.header("Authorization", "Bearer " + apiKey.trim());
        }
        return spec;
    }

    private RestClient.RequestHeadersSpec<?> prepareGet(String uri, Object... uriVariables) {
        var spec = restClient.get()
                .uri(uri, uriVariables)
                .accept(MediaType.APPLICATION_JSON)
                .header("X-Client-Id", clientId)
                .header("X-Client-Instance", channelGateway.getInstanceId());

        if (apiKey != null && !apiKey.isBlank()) {
            spec.header("Authorization", "Bearer " + apiKey.trim());
        }
        return spec;
    }

    public Models.HeartbeatResponse sendHeartbeat(Models.HeartbeatRequest request) {
        return executeWithRetry("heartbeat", () ->
                preparePost("/instances/heartbeat")
                        .body(request)
                        .retrieve()
                        .body(Models.HeartbeatResponse.class)
        );
    }

    public void publishListings(List<Models.ListingItem> listings) {
        executeWithRetry("publishListings", () -> {
            preparePut("/listings")
                    .body(listings)
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    public void publishStock(List<Models.StockItem> stockItems) {
        executeWithRetry("publishStock", () -> {
            preparePut("/stock")
                    .body(stockItems)
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    public Models.FeedResponse getFeed(long after, int limit) {
        try {
            return prepareGet("/feed?after={after}&limit={limit}", after, limit)
                    .retrieve()
                    .body(Models.FeedResponse.class);
        } catch (Exception ex) {
            log.warn("Error fetching Tiangge order feed (after={}): {}", after, ex.getMessage());
            return null;
        }
    }

    public void sendDecision(String orderId, Models.DecisionRequest request) {
        executeWithRetry("sendDecision-" + orderId, () -> {
            try {
                preparePost("/orders/{orderId}/decision", orderId)
                        .body(request)
                        .retrieve()
                        .toBodilessEntity();
                log.info("Successfully reported decision '{}' for Tiangge order {}", request.decision(), orderId);
            } catch (HttpClientErrorException.Conflict conflictEx) {
                log.warn("Tiangge order {} decision conflict (already decided): {}", orderId, conflictEx.getResponseBodyAsString());
            }
            return null;
        });
    }

    public void sendResolution(String orderId, Models.ResolutionRequest request) {
        executeWithRetry("sendResolution-" + orderId, () -> {
            try {
                preparePost("/orders/{orderId}/resolution", orderId)
                        .body(request)
                        .retrieve()
                        .toBodilessEntity();
                log.info("Successfully reported resolution '{}' for backordered order {}", request.status(), orderId);
            } catch (HttpClientErrorException ex) {
                log.warn("Tiangge order {} resolution response error: {} - {}", orderId, ex.getStatusCode(), ex.getResponseBodyAsString());
            }
            return null;
        });
    }

    public void sendCancellationConfirmation(String orderId, Models.CancellationRequest request) {
        executeWithRetry("sendCancellation-" + orderId, () -> {
            try {
                preparePost("/orders/{orderId}/cancellation", orderId)
                        .body(request)
                        .retrieve()
                        .toBodilessEntity();
                log.info("Successfully confirmed cancellation for Tiangge order {}", orderId);
            } catch (HttpClientErrorException ex) {
                log.warn("Tiangge order {} cancellation response error: {} - {}", orderId, ex.getStatusCode(), ex.getResponseBodyAsString());
            }
            return null;
        });
    }

    private <T> T executeWithRetry(String operationName, Operation<T> operation) {
        int attempts = 0;
        int maxAttempts = 3;
        long backoffMs = 500;

        while (attempts < maxAttempts) {
            attempts++;
            try {
                return operation.execute();
            } catch (HttpServerErrorException | ResourceAccessException e) {
                log.warn("Operation '{}' failed on attempt {}/{}: {}", operationName, attempts, maxAttempts, e.getMessage());
                if (attempts < maxAttempts) {
                    try {
                        Thread.sleep(backoffMs);
                        backoffMs *= 2;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted for " + operationName, ie);
                    }
                } else {
                    throw e;
                }
            } catch (HttpClientErrorException e) {
                // Client errors (4xx) should generally not be retried with the same body
                throw e;
            } catch (Exception e) {
                log.warn("Unexpected exception during '{}' on attempt {}/{}: {}", operationName, attempts, maxAttempts, e.getMessage());
                if (attempts >= maxAttempts) {
                    throw new RuntimeException("Operation failed: " + operationName, e);
                }
            }
        }
        return null;
    }

    @FunctionalInterface
    interface Operation<T> {
        T execute();
    }
}
