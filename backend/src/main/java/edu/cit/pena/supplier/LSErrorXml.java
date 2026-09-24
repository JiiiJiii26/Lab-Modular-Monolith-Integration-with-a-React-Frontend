package edu.cit.pena.supplier;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * PACKAGE-PRIVATE — never imported outside edu.cit.pena.supplier.
 * XML error document returned by LegacySupply on failures.
 */
@XmlRootElement(name = "LSError")
@XmlAccessorType(XmlAccessType.FIELD)
class LSErrorXml {

    @XmlElement(name = "Code")
    private String code;

    @XmlElement(name = "Message")
    private String message;

    LSErrorXml() {}

    LSErrorXml(String code, String message) {
        this.code = code;
        this.message = message;
    }

    String getCode()    { return code; }
    String getMessage() { return message; }
}
