/* (c) 2018 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wfs3.response;

import org.apache.commons.io.IOUtils;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.SLDHandler;
import org.geoserver.catalog.StyleHandler;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.catalog.Styles;
import org.geoserver.ows.HttpErrorCodeException;
import org.geoserver.ows.Response;
import org.geoserver.platform.Operation;
import org.geoserver.platform.ServiceException;
import org.geoserver.wfs3.StyleRequest;
import org.geotools.styling.StyledLayerDescriptor;
import org.geotools.util.Version;
import org.springframework.http.HttpStatus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class StyleDocumentResponse extends Response {

    private final Catalog catalog;

    public StyleDocumentResponse(Catalog catalog) {
        super(StyleInfo.class, getStyleFormats());
        this.catalog = catalog;
    }

    private static Set<String> getStyleFormats() {
        Set<String> result = new HashSet<>();
        for (StyleHandler handler : Styles.handlers()) {
            for (Version version : handler.getVersions()) {
                result.add(handler.mimeType(version));
                result.add(handler.getName().toLowerCase());
            }
        }

        return result;
    }

    @Override
    public String getMimeType(Object value, Operation operation) throws ServiceException {
        StyleRequest request = (StyleRequest) operation.getParameters()[0];
        final StyleInfo style = (StyleInfo) value;
        String requestedFormat = getRequestedFormat(request, style);
        final StyleHandler handler = Styles.handler(requestedFormat);
        return handler.mimeType(style.getFormatVersion());
    }

    @Override
    public void write(Object value, OutputStream output, Operation operation)
            throws IOException, ServiceException {
        StyleRequest request = (StyleRequest) operation.getParameters()[0];
        StyleInfo style = (StyleInfo) value;
        String requestedFormat = getRequestedFormat(request, style);

        // if no conversion is needed, push out raw style
        if (Objects.equals(requestedFormat, style.getFormat())) {
            try (final BufferedReader reader = catalog.getResourcePool().readStyle(style)) {
                IOUtils.copy(reader, new OutputStreamWriter(output));
            }
        }

        // otherwise look up handler and convert if possible
        final StyleHandler handler = Styles.handler(requestedFormat);
        if (handler == null || !(handler instanceof SLDHandler)) {
            throw new HttpErrorCodeException(
                    HttpStatus.BAD_REQUEST.value(), "Cannot encode style in " + requestedFormat);
        }
        final StyledLayerDescriptor sld = style.getSLD();
        handler.encode(sld, null, true, output);
    }

    public String getRequestedFormat(StyleRequest request, StyleInfo style) {
        String requestedFormat = request.getOutputFormat();
        if (requestedFormat == null) {
            requestedFormat = style.getFormat();
        }
        if (requestedFormat == null) {
            requestedFormat = SLDHandler.FORMAT;
        }
        return requestedFormat;
    }
}
