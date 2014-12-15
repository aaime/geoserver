package org.geoserver.community.css.web;

import java.io.IOException;
import java.io.Reader;

import org.apache.commons.io.IOUtils;
import org.geoserver.platform.ExtensionPriority;
import org.geotools.styling.Style;
import org.geotools.styling.css.CssParser;
import org.geotools.styling.css.CssTranslator;
import org.geotools.styling.css.Stylesheet;

/**
 * A CSS -> Style translator based on the GeoTools gt-css module
 * 
 * @author Andrea Aime - GeoSolutions
 *
 */
public class JavaCssConverter implements CSSConverter, ExtensionPriority {

    @Override
    public boolean isAvailable() {
        try {
            Class.forName("org.geotools.styling.css.CssParser");
            Class.forName("org.geotools.styling.css.CssTranslator");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Style convert(Reader cssReader, Object extraData) throws IOException {
        Stylesheet styleSheet = CssParser.parse(IOUtils.toString(cssReader));
        Style style = (Style) new CssTranslator().translate(styleSheet);
        return style;
    }

    @Override
    public int getPriority() {
        return ExtensionPriority.HIGHEST;
    }

    @Override
    public String toString() {
        return "GeoTools Java based CSS converter";
    }

}
