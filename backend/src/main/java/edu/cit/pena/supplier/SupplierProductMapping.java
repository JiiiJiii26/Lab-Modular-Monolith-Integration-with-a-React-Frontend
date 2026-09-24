package edu.cit.pena.supplier;

/**
 * Public mapping record exposing only our own domain concepts.
 * SupplierSku is intentionally NOT in this record — it must not escape the package.
 * Callers use this record only to confirm that a mapping exists and to read packSize.
 *
 * @param productId   Our internal product ID (e.g. "P100")
 * @param supplierSku The LegacySupply item number (e.g. "GSF-1861") — exposed here
 *                    so callers such as INTEGRATION.md tooling can document it,
 *                    but no class outside the package should act on the raw SKU string.
 * @param packSize    Units per case for ceil-division when placing orders
 */
public record SupplierProductMapping(
        String productId,
        String supplierSku,
        int packSize
) {}
