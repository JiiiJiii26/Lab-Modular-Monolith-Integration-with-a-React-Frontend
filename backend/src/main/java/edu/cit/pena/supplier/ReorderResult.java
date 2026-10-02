package edu.cit.pena.supplier;

public class ReorderResult {
    private final String orderId;
    private final String buyerRef;
    private final String poNumber;
    private final SupplierOrderStatus status;
    private final int cases;
    private final int units;

    public ReorderResult(String orderId, String buyerRef, String poNumber, SupplierOrderStatus status, int cases, int units) {
        this.orderId = orderId;
        this.buyerRef = buyerRef;
        this.poNumber = poNumber;
        this.status = status;
        this.cases = cases;
        this.units = units;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getBuyerRef() {
        return buyerRef;
    }

    public String getPoNumber() {
        return poNumber;
    }

    public SupplierOrderStatus getStatus() {
        return status;
    }

    public int getCases() {
        return cases;
    }

    public int getUnits() {
        return units;
    }
}
