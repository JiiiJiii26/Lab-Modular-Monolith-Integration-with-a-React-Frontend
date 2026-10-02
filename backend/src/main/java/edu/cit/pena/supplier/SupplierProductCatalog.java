package edu.cit.pena.supplier;

import java.util.Map;

import org.springframework.stereotype.Component;

@Component
class SupplierProductCatalog {

    record SupplierItem(String supplierSku, int packSize) {}

    private final Map<String, SupplierItem> catalog = Map.of(
        "P100", new SupplierItem("GSF-1861", 20),
        "P200", new SupplierItem("GSF-2186", 10),
        "P300", new SupplierItem("GSF-4040", 10)
    );

    public SupplierItem resolve(String productId) {
        SupplierItem item = catalog.get(productId);
        if (item == null) {
            throw new IllegalArgumentException("No supplier SKU mapped for product: " + productId);
        }
        return item;
    }
}