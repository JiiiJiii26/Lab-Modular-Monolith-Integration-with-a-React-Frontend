package edu.cit.pena.supplier;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import java.util.List;

/**
 * PACKAGE-PRIVATE — never imported outside edu.cit.pena.supplier.
 * XML response body from GET /purchase-orders?buyerRef={buyerRef}.
 * The API returns a list envelope even when Count=1 containing <PurchaseOrder> elements.
 */
@XmlRootElement(name = "PurchaseOrderList")
@XmlAccessorType(XmlAccessType.FIELD)
class PurchaseOrderListXml {

    @XmlElement(name = "Count")
    private int count;

    @XmlElement(name = "PurchaseOrder")
    private List<PurchaseOrderStatusXml> orders;

    PurchaseOrderListXml() {}

    int getCount() { return count; }
    List<PurchaseOrderStatusXml> getOrders() { return orders; }
}
