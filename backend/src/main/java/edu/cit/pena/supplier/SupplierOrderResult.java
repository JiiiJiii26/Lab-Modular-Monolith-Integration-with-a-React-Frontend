package edu.cit.pena.supplier;

/**
 * Immutable result DTO returned by every SupplierGateway method.
 * Contains only our own domain concepts — no LegacySupply vocabulary.
 *
 * @param id         PK of the supplier_orders row (null before persistence)
 * @param productId  Our internal product ID
 * @param buyerRef   Our buyer reference (e.g. "RO-42")
 * @param requestId  Idempotency key sent as X-Request-Id
 * @param poNumber   LegacySupply PO number (null until 201 received)
 * @param cases      Quantity sent to LegacySupply (in cases)
 * @param units      Original units requested by the caller
 * @param status     Current lifecycle status
 * @param message    Human-readable description of outcome or failure reason
 */
public record SupplierOrderResult(
        Long id,
        String productId,
        String buyerRef,
        String requestId,
        String poNumber,
        int cases,
        int units,
        SupplierOrderStatus status,
        String message
) {}
