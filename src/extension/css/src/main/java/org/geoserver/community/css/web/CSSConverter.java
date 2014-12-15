package org.geoserver.community.css.web;

import java.io.IOException;
import java.io.Reader;

import org.geotools.styling.Style;

/**
 * Converts a CSS style into a GeoTools Style object
 * 
 * @author Andrea Aime - GeoSolutions
 */
public interface CSSConverter {

    /**
     * Returns true if the converter is available (might check if dependent libraries are available,
     * and so on)
     * 
     * @return
     */
    boolean isAvailable();

    /**
     * Converts a CSS stylesheet into a GeoTools style
     * 
     * @param css
     * @return
     * @throws IOException
     */
    Style convert(Reader cssReader, Object extraData) throws IOException;
}
