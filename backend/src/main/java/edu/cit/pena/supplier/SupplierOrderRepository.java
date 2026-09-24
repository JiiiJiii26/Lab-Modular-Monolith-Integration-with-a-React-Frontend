package edu.cit.pena.supplier;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * PACKAGE-PRIVATE repository for supplier_orders.
 */
interface SupplierOrderRepository extends JpaRepository<SupplierOrder, Long> {

    List<SupplierOrder> findByStatusIn(List<SupplierOrderStatus> statuses);

    List<SupplierOrder> findByStatus(SupplierOrderStatus status);

    List<SupplierOrder> findByStatusInAndPoNumberIsNotNull(List<SupplierOrderStatus> statuses);

    List<SupplierOrder> findByProductIdAndStatusInAndCreatedAtAfter(
            String productId,
            List<SupplierOrderStatus> statuses,
            Instant after
    );

    Optional<SupplierOrder> findByBuyerRef(String buyerRef);

    Optional<SupplierOrder> findByRequestId(String requestId);
}
