package edu.cit.pena.supplier;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * PACKAGE-PRIVATE — never imported outside edu.cit.pena.supplier.
 * XML response body from POST /auth/token.
 */
@XmlRootElement(name = "AuthResponse")
@XmlAccessorType(XmlAccessType.FIELD)
class AuthResponseXml {

    @XmlElement(name = "SessionToken")
    private String sessionToken;

    @XmlElement(name = "IssuedAt")
    private String issuedAt;

    AuthResponseXml() {}

    String getSessionToken() { return sessionToken; }
    String getIssuedAt()     { return issuedAt; }
}
