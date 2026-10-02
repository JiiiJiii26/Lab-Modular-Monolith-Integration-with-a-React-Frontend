package edu.cit.pena.supplier;

public interface SupplierGateway {
    ReorderResult orderReplenishment(String productId, int unitsNeeded);
    boolean hasInFlightOrder(String productId);
}
