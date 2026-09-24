package edu.cit.pena.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PACKAGE-PRIVATE implementation of public SupplierGateway interface.
 * Implements the Anti-Corruption Layer (ACL) for LegacySupply with idempotency,
 * pre-flight deduplication, reconciliation-before-retry, and error handling.
 */
@Service
class SupplierGatewayImpl implements SupplierGateway {

    private static final Logger log = LoggerFactory.getLogger(SupplierGatewayImpl.class);

    /**
     * Package-private carrier for reconciliation results.
     * Avoids exposing any LegacySupply types outside this method chain.
     */
    record ReconciledPo(String poNumber, SupplierOrderStatus status) {}

    private final SupplierOrderRepository orderRepository;
    private final XmlHttpClient xmlHttpClient;

    SupplierGatewayImpl(SupplierOrderRepository orderRepository, XmlHttpClient xmlHttpClient) {
        this.orderRepository = orderRepository;
        this.xmlHttpClient = xmlHttpClient;
    }

    @Override
    @Transactional
    public SupplierOrderResult requestRestock(String productId, int unitsNeeded) {
        log.info("Requesting restock for product={}, unitsNeeded={}", productId, unitsNeeded);

        // a. Look up product mapping
        Optional<SupplierProductMapping> mappingOpt = SupplierTranslator.findMapping(productId);
        if (mappingOpt.isEmpty()) {
            String msg = "No supplier mapping for product " + productId;
            log.warn(msg);
            return new SupplierOrderResult(null, productId, null, null, null, 0, unitsNeeded, SupplierOrderStatus.FAILED, msg);
        }

        SupplierProductMapping mapping = mappingOpt.get();

        // b. Check if active supplier order was created for this product in the last 60 seconds
        Instant sixtySecondsAgo = Instant.now().minus(Duration.ofSeconds(60));
        List<SupplierOrderStatus> inFlightStatuses = List.of(
                SupplierOrderStatus.PENDING,
                SupplierOrderStatus.SENT,
                SupplierOrderStatus.ACKNOWLEDGED,
                SupplierOrderStatus.PICKING,
                SupplierOrderStatus.SHIPPED
        );
        List<SupplierOrder> existingRecent = orderRepository.findByProductIdAndStatusInAndCreatedAtAfter(
                productId, inFlightStatuses, sixtySecondsAgo);
        if (!existingRecent.isEmpty()) {
            SupplierOrder recent = existingRecent.get(0);
            log.info("Active restock order #{} already in flight for product '{}' within last 60s; skipping duplicate.",
                    recent.getId(), productId);
            return SupplierTranslator.toResult(recent, "Existing active order created within last 60s");
        }

        // c. Ceiling division: ceil(unitsNeeded / packSize)
        int cases = SupplierTranslator.calculateCases(unitsNeeded, mapping.packSize());

        // Generate requestId and persist initial PENDING row
        String requestId = UUID.randomUUID().toString();
        SupplierOrder order = new SupplierOrder(productId, "TEMP-" + requestId, requestId, cases, unitsNeeded, SupplierOrderStatus.PENDING);
        order = orderRepository.saveAndFlush(order);

        // Set buyerRef = 'RO-' || id
        String buyerRef = "RO-" + order.getId();
        order.setBuyerRef(buyerRef);
        order = orderRepository.save(order);

        // d. Call the HTTP layer with X-Request-Id = persisted request_id
        try {
            PurchaseOrderXml poXml = new PurchaseOrderXml(mapping.supplierSku(), cases, buyerRef);
            String requestXml = XmlUtils.toXml(poXml);

            HttpResponse<String> response = xmlHttpClient.postXml("/purchase-orders", requestXml, requestId);
            int statusCode = response.statusCode();
            String responseBody = response.body();

            // e. On 201: clean success
            if (statusCode == 201) {
                PurchaseOrderAckXml ack = XmlUtils.fromXml(responseBody, PurchaseOrderAckXml.class);
                SupplierOrderStatus ackStatus = SupplierTranslator.translateStatusCode(ack.getStatusCode());
                if (ackStatus == SupplierOrderStatus.UNKNOWN || ackStatus == SupplierOrderStatus.PENDING) {
                    ackStatus = SupplierOrderStatus.SENT; // default for fresh 201
                }
                order.setPoNumber(ack.getPoNumber());
                order.setStatus(ackStatus);
                order.setUpdatedAt(Instant.now());
                order = orderRepository.save(order);
                log.info("LegacySupply accepted order: poNumber={}, status={}", ack.getPoNumber(), order.getStatus());
                return SupplierTranslator.toResult(order, "Order placed successfully. PoNumber: " + ack.getPoNumber());
            }

            // POST did not return 201 (timeout absorbed by XmlHttpClient is surfaced here as
            // a non-201 code, or we land here on 200/idempotent replay/5xx after all retries).
            // Before giving up: check whether the PO already exists at LegacySupply.
            Optional<ReconciledPo> reconciled = queryBuyerRefList(buyerRef);
            if (reconciled.isPresent()) {
                ReconciledPo rp = reconciled.get();
                order.setPoNumber(rp.poNumber());
                order.setStatus(rp.status());
                order.setUpdatedAt(Instant.now());
                order = orderRepository.save(order);
                log.info("Reconciled {} from LegacySupply: {}, status={}", buyerRef, rp.poNumber(), rp.status());
                return SupplierTranslator.toResult(order, "Reconciled existing order. PoNumber: " + rp.poNumber());
            }

            // f. Permanent 4xx (not 401/429) — reconcile found nothing, mark FAILED
            if (statusCode >= 400 && statusCode < 500 && statusCode != 401 && statusCode != 429) {
                String errorMsg = extractErrorMessage(responseBody, statusCode);
                order.setStatus(SupplierOrderStatus.FAILED);
                order.setUpdatedAt(Instant.now());
                order = orderRepository.save(order);
                log.error("LegacySupply permanent failure (HTTP {}): {}", statusCode, errorMsg);
                return SupplierTranslator.toResult(order, "Permanent supplier failure: " + errorMsg);
            }

            // g. Transient failure — leave PENDING for the resend job
            log.warn("LegacySupply transient failure (HTTP {}): leaving order {} in PENDING state for resend job",
                    statusCode, order.getId());
            return SupplierTranslator.toResult(order, "Supplier request returned HTTP " + statusCode);

        } catch (Exception e) {
            // Timeout or network error after XmlHttpClient exhausted its retries.
            // Check whether LegacySupply already created the PO before we timed out.
            log.warn("LegacySupply request threw exception for order {}: {}", order.getId(), e.getMessage());
            Optional<ReconciledPo> reconciled = queryBuyerRefList(buyerRef);
            if (reconciled.isPresent()) {
                ReconciledPo rp = reconciled.get();
                order.setPoNumber(rp.poNumber());
                order.setStatus(rp.status());
                order.setUpdatedAt(Instant.now());
                order = orderRepository.save(order);
                log.info("Reconciled {} from LegacySupply after exception: {}, status={}", buyerRef, rp.poNumber(), rp.status());
                return SupplierTranslator.toResult(order, "Reconciled existing order after timeout. PoNumber: " + rp.poNumber());
            }
            // PO not found — leave PENDING for the resend job
            log.warn("No existing PO found for {}; leaving order {} in PENDING state.", buyerRef, order.getId());
            return SupplierTranslator.toResult(order, "Error contacting supplier: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public Optional<SupplierOrderResult> queryStatus(Long supplierOrderId) {
        Optional<SupplierOrder> orderOpt = orderRepository.findById(supplierOrderId);
        if (orderOpt.isEmpty()) {
            return Optional.empty();
        }

        SupplierOrder order = orderOpt.get();
        if (order.getPoNumber() == null || order.getPoNumber().isBlank()) {
            return Optional.of(SupplierTranslator.toResult(order, "Order has not yet been assigned a supplier PO number."));
        }

        try {
            HttpResponse<String> response = xmlHttpClient.getXml("/purchase-orders/" + order.getPoNumber());
            if (response.statusCode() == 200) {
                PurchaseOrderStatusXml statusXml = XmlUtils.fromXml(response.body(), PurchaseOrderStatusXml.class);
                SupplierOrderStatus translatedStatus = SupplierTranslator.translateStatusCode(statusXml.getStatusCode());
                order.setStatus(translatedStatus);
                order.setUpdatedAt(Instant.now());
                order = orderRepository.save(order);
                return Optional.of(SupplierTranslator.toResult(order, "Status updated from supplier: " + translatedStatus));
            } else {
                log.warn("Query status for poNumber={} returned HTTP {}", order.getPoNumber(), response.statusCode());
            }
        } catch (Exception e) {
            log.warn("Failed to query supplier status for poNumber={}: {}", order.getPoNumber(), e.getMessage());
        }

        return Optional.of(SupplierTranslator.toResult(order, "Could not refresh status from supplier."));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplierOrderResult> findOpenOrders() {
        List<SupplierOrderStatus> openStatuses = List.of(
                SupplierOrderStatus.PENDING,
                SupplierOrderStatus.SENT,
                SupplierOrderStatus.ACKNOWLEDGED
        );
        return orderRepository.findByStatusIn(openStatuses)
                .stream()
                .map(o -> SupplierTranslator.toResult(o, "Open order"))
                .toList();
    }

    /**
     * Calls GET /purchase-orders?buyerRef={buyerRef} and parses the PurchaseOrderList.
     * Returns the first matching item's PoNumber + translated status, or empty if none found.
     * Used for reconciliation after POST failures or timeouts.
     */
    Optional<ReconciledPo> queryBuyerRefList(String buyerRef) {
        try {
            HttpResponse<String> response = xmlHttpClient.getXml("/purchase-orders?buyerRef=" + buyerRef);
            if (response.statusCode() == 200 && response.body() != null && !response.body().isBlank()) {
                PurchaseOrderListXml list = XmlUtils.fromXml(response.body(), PurchaseOrderListXml.class);
                if (list != null) {
                    int parsedCount = list.getOrders() != null ? list.getOrders().size() : 0;
                    log.info("Reconcile parsed {} items for buyerRef={}", parsedCount, buyerRef);
                    if (list.getOrders() != null) {
                        for (PurchaseOrderStatusXml order : list.getOrders()) {
                            if (buyerRef.equalsIgnoreCase(order.getBuyerRef()) && order.getPoNumber() != null) {
                                SupplierOrderStatus status = SupplierTranslator.translateStatusCode(order.getStatusCode());
                                // If status code translates to UNKNOWN or PENDING, default to SENT for active PO
                                if (status == SupplierOrderStatus.UNKNOWN || status == SupplierOrderStatus.PENDING) {
                                    status = SupplierOrderStatus.SENT;
                                }
                                return Optional.of(new ReconciledPo(order.getPoNumber().trim(), status));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("queryBuyerRefList failed for buyerRef={}: {}", buyerRef, e.getMessage());
        }
        return Optional.empty();
    }

    private String extractErrorMessage(String responseBody, int httpStatus) {
        if (responseBody != null && !responseBody.isBlank()) {
            try {
                LSErrorXml errorXml = XmlUtils.fromXml(responseBody, LSErrorXml.class);
                if (errorXml != null && errorXml.getMessage() != null) {
                    return errorXml.getCode() != null
                            ? errorXml.getCode() + ": " + errorXml.getMessage()
                            : errorXml.getMessage();
                }
            } catch (Exception ignored) {}
        }
        return "HTTP " + httpStatus;
    }
}
