package edu.cit.pena.supplier;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * PACKAGE-PRIVATE — never imported outside edu.cit.pena.supplier.
 * XML response body from a successful POST /purchase-orders (HTTP 201).
 */
@XmlRootElement(name = "PurchaseOrderAck")
@XmlAccessorType(XmlAccessType.FIELD)
class PurchaseOrderAckXml {

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

    PurchaseOrderAckXml() {}

    String getPoNumber()    { return poNumber; }
    int    getStatusCode()  { return statusCode; }
    String getSupplierSku() { return supplierSku; }
    int    getQty()         { return qty; }
    String getUom()         { return uom; }
    String getBuyerRef()    { return buyerRef; }
    String getCreatedAt()   { return createdAt; }
}
