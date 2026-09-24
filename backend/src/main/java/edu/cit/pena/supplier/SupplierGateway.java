package edu.cit.pena.supplier;

import java.util.List;
import java.util.Optional;

/**
 * Public gateway interface — the ONLY entry point into the supplier module
 * for the rest of the application. No XML, no LegacySupply terminology,
 * no SupplierSku strings cross this boundary.
 */
public interface SupplierGateway {

    /**
     * Request a restock from LegacySupply for the given product and unit count.
     * Converts units → cases internally using ceil(unitsNeeded / packSize).
     * Never throws on supplier failure — returns a SupplierOrderResult with
     * status FAILED or PENDING instead.
     *
     * @param productId   Our internal product ID (e.g. "P100")
     * @param unitsNeeded Number of individual units we want to restock
     * @return domain result describing the outcome
     */
    SupplierOrderResult requestRestock(String productId, int unitsNeeded);

    /**
     * Query the current status of a single purchase order by our supplier_orders PK.
     *
     * @param supplierOrderId PK of the supplier_orders row
     * @return optional domain result, empty if the ID is not found
     */
    Optional<SupplierOrderResult> queryStatus(Long supplierOrderId);

    /**
     * Return all supplier orders currently considered open
     * (status = PENDING, SENT, or ACKNOWLEDGED).
     */
    List<SupplierOrderResult> findOpenOrders();
}
