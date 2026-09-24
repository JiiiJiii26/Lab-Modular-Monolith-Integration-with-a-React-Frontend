package edu.cit.pena.supplier;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * PACKAGE-PRIVATE — never imported outside edu.cit.pena.supplier.
 * XML request body for POST /purchase-orders.
 * Field names follow LegacySupply conventions exactly; no exposure outside.
 */
@XmlRootElement(name = "PurchaseOrder")
@XmlAccessorType(XmlAccessType.FIELD)
class PurchaseOrderXml {

    @XmlElement(name = "SupplierSku")
    private String supplierSku;

    @XmlElement(name = "Qty")
    private int qty;

    @XmlElement(name = "BuyerRef")
    private String buyerRef;

    PurchaseOrderXml() {}

    PurchaseOrderXml(String supplierSku, int qty, String buyerRef) {
        this.supplierSku = supplierSku;
        this.qty         = qty;
        this.buyerRef    = buyerRef;
    }

    String getSupplierSku() { return supplierSku; }
    int    getQty()         { return qty; }
    String getBuyerRef()    { return buyerRef; }
}
