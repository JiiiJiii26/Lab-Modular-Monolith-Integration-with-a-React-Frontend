package edu.cit.pena.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;

/**
 * PACKAGE-PRIVATE catalog service.
 * Loads and caches catalog items from LegacySupply.
 */
@Service
class SupplierCatalogService {

    private static final Logger log = LoggerFactory.getLogger(SupplierCatalogService.class);

    private final XmlHttpClient xmlHttpClient;
    private List<CatalogXml.ItemXml> cachedItems = Collections.emptyList();

    SupplierCatalogService(XmlHttpClient xmlHttpClient) {
        this.xmlHttpClient = xmlHttpClient;
    }

    synchronized List<CatalogXml.ItemXml> loadCatalog() {
        try {
            log.info("Fetching catalog from LegacySupply...");
            HttpResponse<String> response = xmlHttpClient.getXml("/catalog");
            if (response.statusCode() == 200) {
                CatalogXml catalog = XmlUtils.fromXml(response.body(), CatalogXml.class);
                if (catalog != null && catalog.getItems() != null) {
                    this.cachedItems = catalog.getItems();
                    log.info("Successfully loaded {} catalog items from LegacySupply.", cachedItems.size());
                    return cachedItems;
                }
            } else {
                log.warn("Catalog fetch returned status {}", response.statusCode());
            }
        } catch (Exception e) {
            log.warn("Could not load catalog from LegacySupply (offline/unconfigured): {}", e.getMessage());
        }
        return cachedItems;
    }

    synchronized List<CatalogXml.ItemXml> getCachedItems() {
        return cachedItems;
    }
}
