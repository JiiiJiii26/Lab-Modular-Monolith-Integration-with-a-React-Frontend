package edu.cit.pena.supplier;

import edu.cit.pena.shared.events.SupplierOrderDeliveredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;

/**
 * PACKAGE-PRIVATE scheduled component.
 * Periodically polls status of open purchase orders from LegacySupply,
 * translates status codes, and publishes SupplierOrderDeliveredEvent upon delivery.
 */
@Component
class SupplierTrackingJob {

    private static final Logger log = LoggerFactory.getLogger(SupplierTrackingJob.class);
    private static final int MAX_ORDERS_PER_TICK = 10;

    private final SupplierOrderRepository orderRepository;
    private final XmlHttpClient xmlHttpClient;
    private final ApplicationEventPublisher eventPublisher;

    SupplierTrackingJob(
            SupplierOrderRepository orderRepository,
            XmlHttpClient xmlHttpClient,
            ApplicationEventPublisher eventPublisher
    ) {
        this.orderRepository = orderRepository;
        this.xmlHttpClient = xmlHttpClient;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelayString = "${supplier.scheduler.poll-fixed-delay-ms}")
    @Transactional
    void pollOpenOrders() {
        List<SupplierOrderStatus> pollableStatuses = List.of(
                SupplierOrderStatus.SENT,
                SupplierOrderStatus.ACKNOWLEDGED,
                SupplierOrderStatus.PICKING,
                SupplierOrderStatus.SHIPPED
        );

        List<SupplierOrder> ordersToPoll = orderRepository.findByStatusInAndPoNumberIsNotNull(pollableStatuses)
                .stream()
                .limit(MAX_ORDERS_PER_TICK)
                .toList();

        if (ordersToPoll.isEmpty()) {
            return;
        }

        log.debug("Polling status for {} open supplier order(s)", ordersToPoll.size());

        for (SupplierOrder order : ordersToPoll) {
            try {
                HttpResponse<String> response = xmlHttpClient.getXml("/purchase-orders/" + order.getPoNumber());
                if (response.statusCode() == 200) {
                    PurchaseOrderStatusXml statusXml = XmlUtils.fromXml(response.body(), PurchaseOrderStatusXml.class);
                    SupplierOrderStatus newStatus = SupplierTranslator.translateStatusCode(statusXml.getStatusCode());

                    if (newStatus == SupplierOrderStatus.UNKNOWN) {
                        log.warn("Unknown LegacySupply StatusCode '{}' for PoNumber {} (order #{})",
                                statusXml.getStatusCode(), order.getPoNumber(), order.getId());
                    }

                    SupplierOrderStatus previousStatus = order.getStatus();
                    order.setStatus(newStatus);
                    order.setUpdatedAt(Instant.now());
                    orderRepository.save(order);

                    log.info("Polled PO {}: previousStatus={}, newStatus={}",
                            order.getPoNumber(), previousStatus, newStatus);

                    if (newStatus == SupplierOrderStatus.DELIVERED && previousStatus != SupplierOrderStatus.DELIVERED) {
                        log.info("Order #{} (PO {}) transitioned to DELIVERED. Publishing SupplierOrderDeliveredEvent (units={})",
                                order.getId(), order.getPoNumber(), order.getUnits());
                        eventPublisher.publishEvent(new SupplierOrderDeliveredEvent(
                                order.getId(),
                                order.getProductId(),
                                order.getUnits(),
                                order.getPoNumber()
                        ));
                    }
                } else {
                    log.warn("Status poll for PO {} returned HTTP {}", order.getPoNumber(), response.statusCode());
                }
            } catch (Exception e) {
                log.warn("Error polling status for PO {}: {}", order.getPoNumber(), e.getMessage());
            }
        }
    }
}
