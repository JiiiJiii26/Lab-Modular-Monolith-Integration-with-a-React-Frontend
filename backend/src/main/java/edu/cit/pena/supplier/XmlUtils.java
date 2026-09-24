package edu.cit.pena.supplier;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;

import java.io.StringReader;
import java.io.StringWriter;

/**
 * PACKAGE-PRIVATE XML serializer/deserializer using standard JAXB.
 */
class XmlUtils {

    @SuppressWarnings("unchecked")
    static <T> T fromXml(String xml, Class<T> clazz) throws JAXBException {
        if (xml == null || xml.isBlank()) {
            throw new JAXBException("Cannot parse empty XML document");
        }
        JAXBContext context = JAXBContext.newInstance(clazz);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        return (T) unmarshaller.unmarshal(new StringReader(xml));
    }

    static String toXml(Object obj) throws JAXBException {
        if (obj == null) return "";
        JAXBContext context = JAXBContext.newInstance(obj.getClass());
        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        StringWriter writer = new StringWriter();
        marshaller.marshal(obj, writer);
        return writer.toString();
    }
}
