/* (c) 2019 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */

package org.geoserver.ogcapi;

import io.swagger.v3.oas.models.OpenAPI;
import org.geoserver.config.GeoServer;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.HashMap;

/**
 * A converter used to build a API browser using Swagger UI
 */
@Component
public class APIHTMLMessageConverter extends AbstractHTMLMessageConverter<OpenAPI> {

    @Override
    public boolean canWrite(Class<?> clazz, MediaType mediaType) {
        return super.canWrite(clazz, mediaType);
    }

    /**
     * Builds a message converter
     *
     * @param support support
     * @param geoServer The
     */
    public APIHTMLMessageConverter(FreemarkerTemplateSupport support, GeoServer geoServer) {
        super(support, geoServer);
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return OpenAPI.class.isAssignableFrom(clazz);
    }

    @Override
    protected void writeInternal(OpenAPI value, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        try {
            APIRequestInfo requestInfo = APIRequestInfo.get();
            HashMap<String, Object> model =
                    setupModel(value, requestInfo.getServiceInfo());
            Charset defaultCharset = getDefaultCharset();
            if (outputMessage != null
                    && outputMessage.getBody() != null
                    && defaultCharset != null) {
                templateSupport.processTemplate(
                        null,
                        "api.ftl",
                        requestInfo.getService().getService().getClass(),
                        model,
                        new OutputStreamWriter(outputMessage.getBody(), defaultCharset),
                        defaultCharset);
            } else {
                LOGGER.warning(
                        "Either the default character set, output message or body was null, so the "
                                + "template could not be processed.");
            }
        } finally {
            // the model can be working over feature collections, make sure they are cleaned up
            purgeIterators();
        }
    }
}
