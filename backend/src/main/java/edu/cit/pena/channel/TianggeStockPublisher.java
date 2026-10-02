package edu.cit.pena.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * PACKAGE-PRIVATE service that publishes stock updates to Tiangge.
 */
@Component
class TianggeStockPublisher {

    private static final Logger log = LoggerFactory.getLogger(TianggeStockPublisher.class);

    private final TianggeHttpClient httpClient;

    TianggeStockPublisher(TianggeHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    void publishStock(Map<String, Integer> stockBySellerSku) {
        try {
            List<TianggeDtos.StockEntry> entries = stockBySellerSku.entrySet().stream()
                    .map(e -> new TianggeDtos.StockEntry(e.getKey(), e.getValue()))
                    .toList();
            var body = new TianggeDtos.PublishStockRequest(entries);
            var response = httpClient.post("/stock", body);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Published stock for {} SKUs to Tiangge", entries.size());
            } else {
                log.warn("Tiangge publishStock failed: HTTP {} body={}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("Tiangge publishStock error: {}", e.getMessage());
        }
    }
}
