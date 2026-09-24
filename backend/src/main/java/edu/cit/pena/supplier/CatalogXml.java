package edu.cit.pena.supplier;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlValue;

import java.util.List;

/**
 * PACKAGE-PRIVATE — never imported outside edu.cit.pena.supplier.
 * XML response from GET /catalog.
 */
@XmlRootElement(name = "Catalog")
@XmlAccessorType(XmlAccessType.FIELD)
class CatalogXml {

    @XmlElement(name = "Item")
    private List<ItemXml> items;

    CatalogXml() {}

    List<ItemXml> getItems() { return items; }

    @XmlAccessorType(XmlAccessType.FIELD)
    static class ItemXml {
        @XmlElement(name = "SupplierSku")
        private String supplierSku;

        @XmlElement(name = "Description")
        private String description;

        @XmlElement(name = "PackSize")
        private int packSize;

        @XmlElement(name = "UnitCost")
        private UnitCostXml unitCost;

        String getSupplierSku()   { return supplierSku; }
        String getDescription()   { return description; }
        int    getPackSize()      { return packSize; }
        UnitCostXml getUnitCost() { return unitCost; }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    static class UnitCostXml {
        @XmlAttribute(name = "currency")
        private String currency;

        @XmlValue
        private String value;

        String getCurrency() { return currency; }
        String getValue()    { return value; }
    }
}
