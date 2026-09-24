package edu.cit.pena.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * PACKAGE-PRIVATE session manager.
 * Handles sign-in to POST /auth/token, caches token in-memory, and provides re-authentication on 401.
 */
@Component
class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    private final String baseUrl;
    private final String clientId;
    private final String apiKey;
    private final Duration timeout;
    private final HttpClient httpClient;

    private String cachedToken;

    SessionManager(
            @Value("${supplier.base-url:https://legacysupply.onrender.com/api/v1}") String baseUrl,
            @Value("${supplier.client-id:}") String clientId,
            @Value("${supplier.api-key:}") String apiKey,
            @Value("${supplier.timeout-ms:3000}") int timeoutMs
    ) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .build();
    }

    synchronized String getOrRefreshSessionToken() {
        if (cachedToken != null && !cachedToken.isBlank()) {
            return cachedToken;
        }
        return authenticate();
    }

    synchronized String forceRefreshSessionToken() {
        this.cachedToken = null;
        return authenticate();
    }

    private synchronized String authenticate() {
        if (clientId == null || clientId.isBlank() || apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("LegacySupply credentials missing: client-id or api-key not set.");
        }

        try {
            String requestXml = XmlUtils.toXml(new AuthRequestXml(clientId, apiKey));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/auth/token"))
                    .timeout(timeout)
                    .header("Content-Type", "application/xml; charset=utf-8")
                    .header("Accept", "application/xml")
                    .POST(HttpRequest.BodyPublishers.ofString(requestXml))
                    .build();

            log.info("Requesting authentication token from LegacySupply at {}/auth/token", baseUrl);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                AuthResponseXml authResp = XmlUtils.fromXml(response.body(), AuthResponseXml.class);
                if (authResp != null && authResp.getSessionToken() != null && !authResp.getSessionToken().isBlank()) {
                    this.cachedToken = authResp.getSessionToken().trim();
                    log.info("Successfully obtained LegacySupply session token (issued at: {})", authResp.getIssuedAt());
                    return this.cachedToken;
                }
            }

            String redactedBody = response.body() != null ? response.body().replace(apiKey, "<REDACTED>") : "";
            log.error("Authentication failed with HTTP status {}: {}", response.statusCode(), redactedBody);
            throw new RuntimeException("LegacySupply authentication failed with HTTP " + response.statusCode());
        } catch (Exception e) {
            log.error("Error during LegacySupply authentication: {}", e.getMessage());
            throw new RuntimeException("LegacySupply authentication error: " + e.getMessage(), e);
        }
    }
}
