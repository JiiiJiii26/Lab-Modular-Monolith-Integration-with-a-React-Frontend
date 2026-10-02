package edu.cit.pena.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * PACKAGE-PRIVATE HTTP client for Tiangge API.
 * Adds X-Client-Id, Authorization: Bearer, and X-Client-Instance headers on every call.
 * TianggeInstanceManager is injected lazily to break the circular dependency
 * (TianggeInstanceManager → TianggeHttpClient → TianggeInstanceManager).
 */
@Component
class TianggeHttpClient {

    private static final Logger log = LoggerFactory.getLogger(TianggeHttpClient.class);

    private final String baseUrl;
    private final String clientId;
    private final String apiKey;
    private final TianggeInstanceManager instanceManager;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    TianggeHttpClient(
            @Value("${channel.tiangge.base-url}") String baseUrl,
            @Value("${supplier.client-id:}") String clientId,
            @Value("${supplier.api-key:}") String apiKey,
            @Lazy TianggeInstanceManager instanceManager,
            ObjectMapper objectMapper
    ) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.instanceManager = instanceManager;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    private String instanceIdHeader() {
        String id = instanceManager.getInstanceId();
        return id != null ? id : "";
    }

    HttpResponse<String> post(String path, Object body) throws Exception {
        String json = objectMapper.writeValueAsString(body);
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + normalizedPath))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-Client-Id", clientId)
                .header("Authorization", "Bearer " + apiKey)
                .header("X-Client-Instance", instanceIdHeader())
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> put(String path, Object body) throws Exception {
        String json = objectMapper.writeValueAsString(body);
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + normalizedPath))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-Client-Id", clientId)
                .header("Authorization", "Bearer " + apiKey)
                .header("X-Client-Instance", instanceIdHeader())
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> get(String path) throws Exception {
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + normalizedPath))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("X-Client-Id", clientId)
                .header("Authorization", "Bearer " + apiKey)
                .header("X-Client-Instance", instanceIdHeader())
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
