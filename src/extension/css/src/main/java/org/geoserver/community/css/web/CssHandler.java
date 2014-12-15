/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.community.css.web;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import org.geoserver.catalog.StyleHandler;
import org.geoserver.catalog.Styles;
import org.geoserver.platform.GeoServerExtensions;
import org.geotools.styling.ResourceLocator;
import org.geotools.styling.Style;
import org.geotools.styling.StyledLayerDescriptor;
import org.geotools.util.Version;
import org.geotools.util.logging.Logging;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.xml.sax.EntityResolver;

/**
 * Style handler for geocss.
 * 
 * @author Justin Deoliveira, Boundless
 * @author Andrea Aime, GeoSolutions
 */
public class CssHandler extends StyleHandler implements ApplicationContextAware {

    static final Logger LOGGER = Logging.getLogger(CssHandler.class);

    public static final String FORMAT = "css";

    public static final String MIME_TYPE = "application/vnd.geoserver.geocss+css";

    private CSSConverter converter;

    protected CssHandler() {
        super("CSS", FORMAT);
    }

    @Override
    public String mimeType(Version version) {
        return MIME_TYPE;
    }

    @Override
    public StyledLayerDescriptor parse(Object input, Version version,
            ResourceLocator resourceLocator, EntityResolver entityResolver) throws IOException {
        try (Reader reader = toReader(input)) {
            Style style = convert(reader, null);
            return Styles.sld(style);
        }
    }

    public Style convert(Reader reader, Object context) throws IOException {
        Style style = converter.convert(reader, context);
        return style;
    }

    @Override
    public void encode(StyledLayerDescriptor sld, Version version, boolean pretty,
            OutputStream output) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Exception> validate(Object input, Version version, EntityResolver entityResolver)
            throws IOException {
        try (Reader reader = toReader(input)) {
            converter.convert(reader, null);
            return Collections.emptyList();
        } catch (Exception e) {
            return Arrays.asList(e);
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        List<CSSConverter> converters = GeoServerExtensions.extensions(CSSConverter.class,
                applicationContext);
        for (CSSConverter converter : converters) {
            if (converter.isAvailable()) {
                this.converter = converter;
                LOGGER.info("Will convert CSS to GeoTools Style objects using: " + converter);
                break;
            }
        }

        if (this.converter == null) {
            LOGGER.severe("Could not find any CSS to Style converter, are the dependent libraries loaded?");
        }

    }
}
