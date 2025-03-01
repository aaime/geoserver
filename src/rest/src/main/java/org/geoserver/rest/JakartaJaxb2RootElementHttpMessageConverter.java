package org.geoserver.rest;

import jakarta.xml.bind.*;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.xml.transform.stream.StreamSource;
import org.springframework.http.*;
import org.springframework.http.converter.*;

/** Custom HttpMessageConverter using jakarta.xml.bind instead of javax.xml.bind */
public class JakartaJaxb2RootElementHttpMessageConverter extends AbstractHttpMessageConverter<Object> {

    private final ConcurrentMap<Class<?>, JAXBContext> contextCache = new ConcurrentHashMap<>(64);

    public JakartaJaxb2RootElementHttpMessageConverter() {
        super(MediaType.APPLICATION_XML, MediaType.TEXT_XML, MediaType.APPLICATION_XHTML_XML);
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return clazz.isAnnotationPresent(XmlRootElement.class) || clazz.isAnnotationPresent(XmlType.class);
    }

    @Override
    protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        try (InputStream inputStream = inputMessage.getBody()) {
            JAXBContext context = JAXBContext.newInstance(clazz);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            return unmarshaller.unmarshal(new StreamSource(inputStream), clazz).getValue();
        } catch (JAXBException ex) {
            throw new HttpMessageNotReadableException(
                    "Could not unmarshal XML to " + clazz.getName(), ex, inputMessage);
        }
    }

    @Override
    protected void writeInternal(Object object, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        try (OutputStream outputStream = outputMessage.getBody()) {
            JAXBContext context = getJAXBContext(object);
            Marshaller marshaller = context.createMarshaller();

            // Configure marshaller properties to match the previous outputq
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.FALSE);
            marshaller.setProperty(Marshaller.JAXB_ENCODING, StandardCharsets.UTF_8.name());
            marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.FALSE);

            // Marshal the object
            marshaller.marshal(object, outputStream);
        } catch (JAXBException ex) {
            ex.printStackTrace();
            throw new HttpMessageNotWritableException(
                    "Could not marshal object to XML: " + object.getClass().getName(), ex);
        }
    }

    /** Creating a new JAXB context is expensive, cache it for reuse. */
    private JAXBContext getJAXBContext(Object object) {
        return contextCache.computeIfAbsent(object.getClass(), c -> {
            try {
                return JAXBContext.newInstance(c);
            } catch (JAXBException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
