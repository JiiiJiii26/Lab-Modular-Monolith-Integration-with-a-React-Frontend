package edu.cit.pena.supplier;

import edu.cit.pena.supplier.event.SupplierOrderDeliveredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@ConditionalOnProperty(name = "supplier.poller.enabled", havingValue = "true", matchIfMissing = true)
class SupplierOrderPoller {

    private static final Logger log = LoggerFactory.getLogger(SupplierOrderPoller.class);

    private final SupplierOrderRepository repository;
    private final LegacySupplyHttpClient client;
    private final SupplierProductCatalog catalog;
    private final SupplierGatewayImpl gateway;
    private final ApplicationEventPublisher eventPublisher;

    public SupplierOrderPoller(
            SupplierOrderRepository repository,
            LegacySupplyHttpClient client,
            SupplierProductCatalog catalog,
            SupplierGatewayImpl gateway,
            ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.client = client;
        this.catalog = catalog;
        this.gateway = gateway;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(initialDelay = 5000, fixedDelay = 10000)
    public void retryPendingOrders() {
        List<SupplierOrder> pending = repository.findByStatus(SupplierOrderStatus.PENDING);
        for (SupplierOrder order : pending) {
            log.info("Background poller retrying pending supplier order: {}", order.getId());
            try {
                String sku = catalog.resolve(order.getProductId()).supplierSku();
                gateway.dispatchOrder(order, sku);
            } catch (Exception ex) {
                log.warn("Background retry failed for {}: {}", order.getId(), ex.getMessage());
            }
        }
    }

    @Scheduled(initialDelay = 5000, fixedDelay = 8000)
    public void pollActiveOrders() {
        List<SupplierOrder> activeOrders = repository.findByStatusIn(List.of(
            SupplierOrderStatus.ACCEPTED,
            SupplierOrderStatus.PICKING,
            SupplierOrderStatus.SHIPPED
        ));

        for (SupplierOrder order : activeOrders) {
            if (order.getPoNumber() == null) continue;

            try {
                // Sleep 1.2s between polling requests to be polite to LegacySupply rate limits
                try {
                    Thread.sleep(1200);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }

                PurchaseOrderStatusResponse status = client.checkOrderStatus(order.getPoNumber());
                SupplierOrderStatus newStatus = mapStatusCode(status.statusCode);

                if (newStatus != order.getStatus()) {
                    log.info("Order {} transitioned from {} to {}", order.getPoNumber(), order.getStatus(), newStatus);
                    order.setStatus(newStatus);
                    repository.save(order);

                    if (newStatus == SupplierOrderStatus.DELIVERED) {
                        log.info("PO {} delivered! Publishing restock event for product {} ({} units)",
                                order.getPoNumber(), order.getProductId(), order.getUnits());
                        eventPublisher.publishEvent(new SupplierOrderDeliveredEvent(
                                order.getPoNumber(),
                                order.getProductId(),
                                order.getUnits(),
                                LocalDateTime.now()
                        ));
                    }
                }
            } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests rateEx) {
                log.warn("Rate limited checking PO status. Backing off: {}", rateEx.getMessage());
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                break;
            } catch (Exception ex) {
                log.warn("Failed checking status for PO {}: {}", order.getPoNumber(), ex.getMessage());
            }
        }
    }

    private SupplierOrderStatus mapStatusCode(String code) {
        return switch (code) {
            case "10" -> SupplierOrderStatus.ACCEPTED;
            case "20" -> SupplierOrderStatus.PICKING;
            case "30" -> SupplierOrderStatus.SHIPPED;
            case "40" -> SupplierOrderStatus.DELIVERED;
            case "50", "90", "99" -> SupplierOrderStatus.CANCELLED;
            default -> {
                log.warn("Unrecognized LegacySupply status code: {}", code);
                yield SupplierOrderStatus.UNKNOWN;
            }
        };
    }
}