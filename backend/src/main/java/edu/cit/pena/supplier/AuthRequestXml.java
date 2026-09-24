package edu.cit.pena.supplier;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * PACKAGE-PRIVATE — never imported outside edu.cit.pena.supplier.
 * XML request body for POST /auth/token.
 */
@XmlRootElement(name = "AuthRequest")
@XmlAccessorType(XmlAccessType.FIELD)
class AuthRequestXml {

    @XmlElement(name = "ClientId")
    private String clientId;

    @XmlElement(name = "ApiKey")
    private String apiKey;

    AuthRequestXml() {}

    AuthRequestXml(String clientId, String apiKey) {
        this.clientId = clientId;
        this.apiKey = apiKey;
    }

    String getClientId() { return clientId; }
    String getApiKey()   { return apiKey; }
}
