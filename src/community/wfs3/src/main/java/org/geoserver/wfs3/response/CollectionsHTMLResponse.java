/*
 *  (c) 2018 Open Source Geospatial Foundation - all rights reserved
 *  * This code is licensed under the GPL 2.0 license, available at the root
 *  * application directory.
 *
 */
package org.geoserver.wfs3.response;

import java.io.IOException;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.config.GeoServer;
import org.geoserver.platform.GeoServerResourceLoader;

public class CollectionsHTMLResponse extends AbstractHTMLResponse {

    public CollectionsHTMLResponse(GeoServerResourceLoader loader, GeoServer geoServer)
            throws IOException {
        super(CollectionsDocument.class, loader, geoServer);
    }

    @Override
    protected String getTemplateName(Object value) {
        return "collections.ftl";
    }

    @Override
    protected ResourceInfo getResource(Object value) {
        return null;
    }
}
