package edu.cit.pena.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Arrays;

/**
 * PACKAGE-PRIVATE HTTP client wrapper.
 * Handles timeouts, authentication header injection (X-LS-Session),
 * idempotency header (X-Request-Id), 401 re-authentication,
 * and retry-with-backoff on transient failures (5xx, 429, IO/timeout exceptions).
 */
@Component
class XmlHttpClient {

    private static final Logger log = LoggerFactory.getLogger(XmlHttpClient.class);

    private final String baseUrl;
    private final Duration timeout;
    private final int maxAttempts;
    private final long[] backoffMs;
    private final SessionManager sessionManager;
    private final HttpClient httpClient;

    XmlHttpClient(
            @Value("${supplier.base-url:https://legacysupply.onrender.com/api/v1}") String baseUrl,
            @Value("${supplier.timeout-ms:3000}") int timeoutMs,
            @Value("${supplier.retry.max-attempts:3}") int maxAttempts,
            @Value("${supplier.retry.backoff-ms:200,500,1200}") String backoffConfig,
            SessionManager sessionManager
    ) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.backoffMs = parseBackoffConfig(backoffConfig);
        this.sessionManager = sessionManager;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .build();
    }

    private static long[] parseBackoffConfig(String config) {
        if (config == null || config.isBlank()) {
            return new long[]{200, 500, 1200};
        }
        try {
            return Arrays.stream(config.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .mapToLong(Long::parseLong)
                    .toArray();
        } catch (Exception e) {
            log.warn("Failed to parse supplier.retry.backoff-ms '{}', using default [200, 500, 1200]", config);
            return new long[]{200, 500, 1200};
        }
    }

    private long getBackoffDelay(int attempt) {
        int index = attempt - 2;
        if (index < 0) return 0;
        if (index < backoffMs.length) {
            return backoffMs[index];
        }
        return backoffMs[backoffMs.length - 1];
    }

    /**
     * Executes a POST request with XML payload and stable X-Request-Id header.
     * Retries up to maxAttempts on transient failures (5xx, 429, timeouts).
     * On 401, re-authenticates once and retries with the same X-Request-Id without consuming an attempt budget.
     */
    HttpResponse<String> postXml(String path, String xmlBody, String requestId) throws Exception {
        HttpResponse<String> lastResponse = null;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (attempt > 1) {
                long delay = getBackoffDelay(attempt);
                log.info("[RETRY {}/{}] Waiting {}ms before retrying POST {}", attempt, maxAttempts, delay, path);
                sleepQuietly(delay);
            }

            try {
                String token = sessionManager.getOrRefreshSessionToken();
                HttpRequest request = buildPostRequest(path, xmlBody, requestId, token);
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                // Handle 401 within the attempt (does not count towards maxAttempts budget)
                if (response.statusCode() == 401) {
                    log.warn("Received 401 from LegacySupply on POST {}. Re-authenticating and retrying once with same X-Request-Id.", path);
                    String freshToken = sessionManager.forceRefreshSessionToken();
                    HttpRequest retryRequest = buildPostRequest(path, xmlBody, requestId, freshToken);
                    response = httpClient.send(retryRequest, HttpResponse.BodyHandlers.ofString());
                }

                if (isRetryableStatus(response.statusCode())) {
                    log.warn("POST {} returned transient status code {}", path, response.statusCode());
                    lastResponse = response;
                    continue; // retry loop
                }

                // Non-retryable status (2xx success or permanent 4xx)
                return response;

            } catch (SocketTimeoutException | HttpTimeoutException e) {
                log.warn("POST {} timed out on attempt {}/{}: {}", path, attempt, maxAttempts, e.getMessage());
                lastException = e;
            } catch (IOException e) {
                log.warn("POST {} network I/O error on attempt {}/{}: {}", path, attempt, maxAttempts, e.getMessage());
                lastException = e;
            } catch (Exception e) {
                // Non-retryable unexpected exception (e.g. IllegalArgumentException, unhandled runtime exception)
                throw e;
            }
        }

        if (lastResponse != null) {
            return lastResponse;
        }
        throw lastException != null ? lastException : new IOException("POST " + path + " failed after " + maxAttempts + " attempts");
    }

    /**
     * Executes a GET request expecting XML payload.
     * Retries up to maxAttempts on transient failures (5xx, 429, timeouts).
     */
    HttpResponse<String> getXml(String path) throws Exception {
        HttpResponse<String> lastResponse = null;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (attempt > 1) {
                long delay = getBackoffDelay(attempt);
                log.info("[RETRY {}/{}] Waiting {}ms before retrying GET {}", attempt, maxAttempts, delay, path);
                sleepQuietly(delay);
            }

            try {
                String token = sessionManager.getOrRefreshSessionToken();
                HttpRequest request = buildGetRequest(path, token);
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                // Handle 401 within the attempt
                if (response.statusCode() == 401) {
                    log.warn("Received 401 from LegacySupply on GET {}. Re-authenticating and retrying once.", path);
                    String freshToken = sessionManager.forceRefreshSessionToken();
                    HttpRequest retryRequest = buildGetRequest(path, freshToken);
                    response = httpClient.send(retryRequest, HttpResponse.BodyHandlers.ofString());
                }

                if (isRetryableStatus(response.statusCode())) {
                    log.warn("GET {} returned transient status code {}", path, response.statusCode());
                    lastResponse = response;
                    continue;
                }

                return response;

            } catch (SocketTimeoutException | HttpTimeoutException e) {
                log.warn("GET {} timed out on attempt {}/{}: {}", path, attempt, maxAttempts, e.getMessage());
                lastException = e;
            } catch (IOException e) {
                log.warn("GET {} network I/O error on attempt {}/{}: {}", path, attempt, maxAttempts, e.getMessage());
                lastException = e;
            } catch (Exception e) {
                throw e;
            }
        }

        if (lastResponse != null) {
            return lastResponse;
        }
        throw lastException != null ? lastException : new IOException("GET " + path + " failed after " + maxAttempts + " attempts");
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode == 429 || (statusCode >= 500 && statusCode < 600);
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during retry backoff", e);
        }
    }

    private HttpRequest buildPostRequest(String path, String xmlBody, String requestId, String token) {
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + normalizedPath))
                .timeout(timeout)
                .header("Content-Type", "application/xml; charset=utf-8")
                .header("Accept", "application/xml")
                .header("X-LS-Session", token)
                .POST(HttpRequest.BodyPublishers.ofString(xmlBody));

        if (requestId != null && !requestId.isBlank()) {
            builder.header("X-Request-Id", requestId);
        }

        return builder.build();
    }

    private HttpRequest buildGetRequest(String path, String token) {
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + normalizedPath))
                .timeout(timeout)
                .header("Accept", "application/xml")
                .header("X-LS-Session", token)
                .GET()
                .build();
    }
}
