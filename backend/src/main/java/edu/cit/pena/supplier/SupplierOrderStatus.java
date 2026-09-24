package edu.cit.pena.supplier;

/**
 * Our domain enum for supplier order lifecycle.
 * Raw LegacySupply integer status codes (10, 20, 30, 40) are translated
 * to this enum inside SupplierTranslator and NEVER escape the package.
 */
public enum SupplierOrderStatus {
    PENDING,        // Row created locally; not yet sent to LegacySupply
    SENT,           // LegacySupply returned 201; we have a PoNumber
    ACKNOWLEDGED,   // Their StatusCode 10 (Accepted)
    PICKING,        // Their StatusCode 20
    SHIPPED,        // Their StatusCode 30
    DELIVERED,      // Their StatusCode 40 — triggers inventory restock callback
    FAILED,         // Permanent failure: 4xx (other than 401/429), XML parse error
    UNKNOWN         // LegacySupply returned a StatusCode we do not recognise
}
