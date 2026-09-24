package edu.cit.pena.supplier;

import java.util.Map;
import java.util.Optional;

/**
 * PACKAGE-PRIVATE translator component.
 * Maps domain product IDs and quantities to supplier SKUs and case counts,
 * and maps raw supplier integer status codes to our internal SupplierOrderStatus enum.
 */
class SupplierTranslator {

    private static final Map<String, SupplierProductMapping> MAPPINGS = Map.of(
            "P100", new SupplierProductMapping("P100", "GSF-1861", 20),
            "P200", new SupplierProductMapping("P200", "GSF-2186", 10),
            "P300", new SupplierProductMapping("P300", "GSF-4040", 10)
    );

    static Optional<SupplierProductMapping> findMapping(String productId) {
        if (productId == null) return Optional.empty();
        return Optional.ofNullable(MAPPINGS.get(productId));
    }

    /**
     * Integer ceiling division: ceil(unitsNeeded / packSize)
     */
    static int calculateCases(int unitsNeeded, int packSize) {
        if (unitsNeeded <= 0 || packSize <= 0) return 0;
        return (unitsNeeded + packSize - 1) / packSize;
    }

    /**
     * Maps LegacySupply integer status codes to our SupplierOrderStatus enum:
     * 10 -> ACKNOWLEDGED
     * 20 -> PICKING
     * 30 -> SHIPPED
     * 40 -> DELIVERED
     * Anything else -> UNKNOWN
     */
    static SupplierOrderStatus translateStatusCode(int statusCode) {
        return switch (statusCode) {
            case 10 -> SupplierOrderStatus.ACKNOWLEDGED;
            case 20 -> SupplierOrderStatus.PICKING;
            case 30 -> SupplierOrderStatus.SHIPPED;
            case 40 -> SupplierOrderStatus.DELIVERED;
            default -> SupplierOrderStatus.UNKNOWN;
        };
    }

    static SupplierOrderResult toResult(SupplierOrder order, String message) {
        if (order == null) return null;
        return new SupplierOrderResult(
                order.getId(),
                order.getProductId(),
                order.getBuyerRef(),
                order.getRequestId(),
                order.getPoNumber(),
                order.getCases(),
                order.getUnits(),
                order.getStatus(),
                message
        );
    }
}
