package edu.cit.pena.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * PACKAGE-PRIVATE scheduled component.
 * Periodically checks for PENDING supplier orders and resends them
 * using the persisted X-Request-Id and BuyerRef to guarantee no order is lost.
 *
 * RECONCILE-FIRST strategy: for every PENDING row, before attempting a POST,
 * we first call GET /purchase-orders?buyerRef={buyerRef}. This catches the
 * scenario where a previous POST timed out on our side AFTER LegacySupply had
 * already created the PO — in that case we discover the existing PO and mark
 * the row SENT without sending a duplicate POST.
 */
@Component
class SupplierResendJob {

    private static final Logger log = LoggerFactory.getLogger(SupplierResendJob.class);

    private final SupplierOrderRepository orderRepository;
    private final XmlHttpClient xmlHttpClient;
    private final SupplierGatewayImpl supplierGateway;

    SupplierResendJob(
            SupplierOrderRepository orderRepository,
            XmlHttpClient xmlHttpClient,
            SupplierGatewayImpl supplierGateway
    ) {
        this.orderRepository = orderRepository;
        this.xmlHttpClient = xmlHttpClient;
        this.supplierGateway = supplierGateway;
    }

    @Scheduled(fixedDelayString = "${supplier.scheduler.resend-fixed-delay-ms}")
    @Transactional
    void resendPending() {
        List<SupplierOrder> pendingOrders = orderRepository.findByStatus(SupplierOrderStatus.PENDING);
        int total = pendingOrders.size();
        if (total == 0) {
            return;
        }

        int sent = 0;
        int stillPending = 0;
        int failed = 0;

        for (SupplierOrder order : pendingOrders) {
            // ── RECONCILE-FIRST ────────────────────────────────────────────────────
            // Check whether LegacySupply already has a PO for this buyerRef before
            // sending a fresh POST. This is the primary fix for the "POST timed out
            // but supplier already created PO" scenario.
            Optional<SupplierGatewayImpl.ReconciledPo> precheck = supplierGateway.queryBuyerRefList(order.getBuyerRef());
            if (precheck.isPresent()) {
                SupplierGatewayImpl.ReconciledPo rp = precheck.get();
                order.setPoNumber(rp.poNumber());
                order.setStatus(rp.status());
                order.setUpdatedAt(Instant.now());
                orderRepository.save(order);
                sent++;
                log.info("Reconciled {} from LegacySupply: {}, status={}", order.getBuyerRef(), rp.poNumber(), rp.status());
                continue; // no POST needed
            }
            // ── END RECONCILE-FIRST ────────────────────────────────────────────────

            Optional<SupplierProductMapping> mappingOpt = SupplierTranslator.findMapping(order.getProductId());
            if (mappingOpt.isEmpty()) {
                log.error("Resend job: No mapping for product '{}' on order #{}, marking FAILED",
                        order.getProductId(), order.getId());
                order.setStatus(SupplierOrderStatus.FAILED);
                order.setUpdatedAt(Instant.now());
                orderRepository.save(order);
                failed++;
                continue;
            }

            SupplierProductMapping mapping = mappingOpt.get();

            try {
                PurchaseOrderXml poXml = new PurchaseOrderXml(mapping.supplierSku(), order.getCases(), order.getBuyerRef());
                String requestXml = XmlUtils.toXml(poXml);

                HttpResponse<String> response = xmlHttpClient.postXml("/purchase-orders", requestXml, order.getRequestId());
                int statusCode = response.statusCode();
                String responseBody = response.body();

                if (statusCode == 201) {
                    PurchaseOrderAckXml ack = XmlUtils.fromXml(responseBody, PurchaseOrderAckXml.class);
                    SupplierOrderStatus ackStatus = SupplierTranslator.translateStatusCode(ack.getStatusCode());
                    if (ackStatus == SupplierOrderStatus.UNKNOWN || ackStatus == SupplierOrderStatus.PENDING) {
                        ackStatus = SupplierOrderStatus.SENT;
                    }
                    order.setPoNumber(ack.getPoNumber());
                    order.setStatus(ackStatus);
                    order.setUpdatedAt(Instant.now());
                    orderRepository.save(order);
                    sent++;
                    log.info("Resend job: Order #{} accepted by supplier with PO {}", order.getId(), ack.getPoNumber());
                    continue;
                }

                // POST did not return 201. Reconcile before deciding on failure type —
                // handles 200 idempotent replay and any other non-201 success-like response.
                Optional<SupplierGatewayImpl.ReconciledPo> postReconcile = supplierGateway.queryBuyerRefList(order.getBuyerRef());
                if (postReconcile.isPresent()) {
                    SupplierGatewayImpl.ReconciledPo rp = postReconcile.get();
                    order.setPoNumber(rp.poNumber());
                    order.setStatus(rp.status());
                    order.setUpdatedAt(Instant.now());
                    orderRepository.save(order);
                    sent++;
                    log.info("Reconciled {} from LegacySupply after POST (HTTP {}): {}, status={}",
                            order.getBuyerRef(), statusCode, rp.poNumber(), rp.status());
                    continue;
                }

                // Permanent 4xx failure — no PO found, no point retrying
                if (statusCode >= 400 && statusCode < 500 && statusCode != 401 && statusCode != 429) {
                    order.setStatus(SupplierOrderStatus.FAILED);
                    order.setUpdatedAt(Instant.now());
                    orderRepository.save(order);
                    failed++;
                    log.error("Resend job: Order #{} permanent failure (HTTP {})", order.getId(), statusCode);
                    continue;
                }

                // Transient failure — leave PENDING
                stillPending++;
                log.info("Resend job: Order #{} transient failure (HTTP {}), remaining PENDING", order.getId(), statusCode);

            } catch (Exception e) {
                // Network/timeout error during POST. Try reconcile once more.
                Optional<SupplierGatewayImpl.ReconciledPo> exceptionReconcile = supplierGateway.queryBuyerRefList(order.getBuyerRef());
                if (exceptionReconcile.isPresent()) {
                    SupplierGatewayImpl.ReconciledPo rp = exceptionReconcile.get();
                    order.setPoNumber(rp.poNumber());
                    order.setStatus(rp.status());
                    order.setUpdatedAt(Instant.now());
                    orderRepository.save(order);
                    sent++;
                    log.info("Reconciled {} from LegacySupply after exception: {}, status={}", order.getBuyerRef(), rp.poNumber(), rp.status());
                } else {
                    stillPending++;
                    log.info("Resend job: Order #{} encountered error: {}, remaining PENDING", order.getId(), e.getMessage());
                }
            }
        }

        log.info("Resend job: checked {} pending, sent {}, still pending {}, failed {}", total, sent, stillPending, failed);
    }
}
