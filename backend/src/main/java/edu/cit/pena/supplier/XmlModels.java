package edu.cit.pena.supplier;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

// Authentication
@JacksonXmlRootElement(localName = "AuthRequest")
class AuthRequest {
    @JacksonXmlProperty(localName = "ClientId")
    public String clientId;
    @JacksonXmlProperty(localName = "ApiKey")
    public String apiKey;

    public AuthRequest() {}
    public AuthRequest(String clientId, String apiKey) {
        this.clientId = clientId;
        this.apiKey = apiKey;
    }
}

@JacksonXmlRootElement(localName = "AuthResponse")
class AuthResponse {
    @JacksonXmlProperty(localName = "SessionToken")
    public String sessionToken;
    @JacksonXmlProperty(localName = "IssuedAt")
    public String issuedAt;
}

// Purchase Order Submit
@JacksonXmlRootElement(localName = "PurchaseOrder")
class PurchaseOrderXmlRequest {
    @JacksonXmlProperty(localName = "SupplierSku")
    public String supplierSku;
    @JacksonXmlProperty(localName = "Qty")
    public int qty;
    @JacksonXmlProperty(localName = "BuyerRef")
    public String buyerRef;

    public PurchaseOrderXmlRequest() {}
    public PurchaseOrderXmlRequest(String supplierSku, int qty, String buyerRef) {
        this.supplierSku = supplierSku;
        this.qty = qty;
        this.buyerRef = buyerRef;
    }
}

@JacksonXmlRootElement(localName = "PurchaseOrderAck")
class PurchaseOrderAck {
    @JacksonXmlProperty(localName = "PoNumber")
    public String poNumber;
    @JacksonXmlProperty(localName = "StatusCode")
    public String statusCode;
    @JacksonXmlProperty(localName = "SupplierSku")
    public String supplierSku;
    @JacksonXmlProperty(localName = "Qty")
    public int qty;
    @JacksonXmlProperty(localName = "Uom")
    public String uom;
    @JacksonXmlProperty(localName = "BuyerRef")
    public String buyerRef;
    @JacksonXmlProperty(localName = "CreatedAt")
    public String createdAt;
}

@JacksonXmlRootElement(localName = "PurchaseOrderStatus")
class PurchaseOrderStatusResponse {
    @JacksonXmlProperty(localName = "PoNumber")
    public String poNumber;
    @JacksonXmlProperty(localName = "StatusCode")
    public String statusCode;
    @JacksonXmlProperty(localName = "SupplierSku")
    public String supplierSku;
    @JacksonXmlProperty(localName = "Qty")
    public int qty;
    @JacksonXmlProperty(localName = "CheckedAt")
    public String checkedAt;
}

@JacksonXmlRootElement(localName = "LSError")
class LSError {
    @JacksonXmlProperty(localName = "Code")
    public String code;
    @JacksonXmlProperty(localName = "Message")
    public String message;
}