package edu.cit.pena.supplier;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * PACKAGE-PRIVATE — never imported outside edu.cit.pena.supplier.
 * XML response from GET /purchase-orders/{PoNumber}.
 * Same fields as PurchaseOrderAck plus CheckedAt.
 */
@XmlRootElement(name = "PurchaseOrderStatus")
@XmlAccessorType(XmlAccessType.FIELD)
class PurchaseOrderStatusXml {

    @XmlElement(name = "PoNumber")
    private String poNumber;

    @XmlElement(name = "StatusCode")
    private int statusCode;

    @XmlElement(name = "SupplierSku")
    private String supplierSku;

    @XmlElement(name = "Qty")
    private int qty;

    @XmlElement(name = "Uom")
    private String uom;

    @XmlElement(name = "BuyerRef")
    private String buyerRef;

    @XmlElement(name = "CreatedAt")
    private String createdAt;

    @XmlElement(name = "CheckedAt")
    private String checkedAt;

    PurchaseOrderStatusXml() {}

    String getPoNumber()    { return poNumber; }
    int    getStatusCode()  { return statusCode; }
    String getSupplierSku() { return supplierSku; }
    int    getQty()         { return qty; }
    String getUom()         { return uom; }
    String getBuyerRef()    { return buyerRef; }
    String getCreatedAt()   { return createdAt; }
    String getCheckedAt()   { return checkedAt; }
}
