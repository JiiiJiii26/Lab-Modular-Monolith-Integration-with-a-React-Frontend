package edu.cit.pena.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
class SupplierGatewayImpl implements SupplierGateway {

    private static final Logger log = LoggerFactory.getLogger(SupplierGatewayImpl.class);

    private final SupplierProductCatalog catalog;
    private final LegacySupplyHttpClient client;
    private final SupplierOrderRepository repository;

    public SupplierGatewayImpl(SupplierProductCatalog catalog, LegacySupplyHttpClient client, SupplierOrderRepository repository) {
        this.catalog = catalog;
        this.client = client;
        this.repository = repository;
    }

    @Override
    @Transactional
    public ReorderResult orderReplenishment(String productId, int unitsNeeded) {
        SupplierProductCatalog.SupplierItem item = catalog.resolve(productId);

        int cases = (int) Math.ceil((double) unitsNeeded / item.packSize());
        if (cases < 1) cases = 1;
        int totalUnits = cases * item.packSize();

        String orderId = "SO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String buyerRef = "RO-" + orderId;
        String requestId = UUID.randomUUID().toString();

        SupplierOrder order = new SupplierOrder(orderId, productId, buyerRef, requestId, cases, totalUnits, SupplierOrderStatus.PENDING);
        repository.save(order);

        dispatchOrder(order, item.supplierSku());

        return new ReorderResult(order.getId(), order.getBuyerRef(), order.getPoNumber(), order.getStatus(), order.getCases(), order.getUnits());
    }

    void dispatchOrder(SupplierOrder order, String supplierSku) {
        PurchaseOrderXmlRequest xmlReq = new PurchaseOrderXmlRequest(supplierSku, order.getCases(), order.getBuyerRef());

        int attempts = 0;
        int maxAttempts = 3;
        long backoff = 500;

        while (attempts < maxAttempts) {
            attempts++;
            try {
                log.info("Submitting order {} to LegacySupply (attempt {}/{})", order.getId(), attempts, maxAttempts);
                PurchaseOrderAck ack = client.sendPurchaseOrder(order.getRequestId(), xmlReq);

                order.setPoNumber(ack.poNumber);
                order.setStatus(SupplierOrderStatus.ACCEPTED);
                repository.save(order);
                log.info("Purchase order accepted by LegacySupply. PO Number: {}", ack.poNumber);
                return;
            } catch (Exception e) {
                log.warn("Attempt {} failed for order {}: {}", attempts, order.getId(), e.getMessage());
                if (attempts < maxAttempts) {
                    try {
                        Thread.sleep(backoff);
                        backoff *= 2; 
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.error("All 3 attempts failed for order {}. Retaining as PENDING for background poller.", order.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasInFlightOrder(String productId) {
        java.util.List<SupplierOrder> inFlight = repository.findByProductIdAndStatusIn(productId, java.util.List.of(
                SupplierOrderStatus.PENDING,
                SupplierOrderStatus.ACCEPTED,
                SupplierOrderStatus.PICKING,
                SupplierOrderStatus.SHIPPED
        ));
        return !inFlight.isEmpty();
    }
}