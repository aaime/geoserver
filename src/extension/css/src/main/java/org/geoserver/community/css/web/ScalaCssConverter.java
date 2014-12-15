/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.community.css.web;

import java.io.File;
import java.io.IOException;
import java.io.Reader;

import org.geoscript.geocss.CssParser;
import org.geoscript.geocss.Translator;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.config.GeoServerDataDirectory;
import org.geotools.styling.Style;

/**
 * A CSS -> Style converter based on the geoscript scala library
 * 
 * @author Andrea Aime - GeoSolutions
 *
 */
public class ScalaCssConverter implements CSSConverter {

    GeoServerDataDirectory dataDir;

    public ScalaCssConverter(GeoServerDataDirectory dataDir) {
        this.dataDir = dataDir;
    }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName("org.geoscript.geocss.CssParser");
            Class.forName("org.geoscript.geocss.Translator");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Style convert(Reader cssReader, Object extraData) throws IOException {
        File styleDir;
        if (extraData instanceof WorkspaceInfo) {
            WorkspaceInfo wi = (WorkspaceInfo) extraData;
            File ws = dataDir.findOrCreateWorkspaceDir(wi);
            styleDir = new File(ws, "styles");
            if (!styleDir.exists()) {
                styleDir.mkdir();
            }
        } else {
            styleDir = dataDir.findStyleDir();
        }

        scala.collection.Seq<org.geoscript.geocss.Rule> rules = CssParser.parse(cssReader).get();
        Translator translator = new Translator(scala.Option.apply(styleDir.toURI().toURL()));
        Style style = translator.css2sld(rules);
        return style;
    }

    @Override
    public String toString() {
        return "GeoScript Scala based CSS converter";
    }
}
