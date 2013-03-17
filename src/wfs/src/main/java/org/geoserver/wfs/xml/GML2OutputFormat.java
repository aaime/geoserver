/* Copyright (c) 2001 - 2013 OpenPlans - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wfs.xml;

import static org.geoserver.ows.util.ResponseUtils.buildSchemaURL;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;

import org.geoserver.catalog.Catalog;
import org.geoserver.config.GeoServer;
import org.geoserver.gml.GML2Writer;
import org.geoserver.platform.Operation;
import org.geoserver.platform.ServiceException;
import org.geoserver.wfs.GMLInfo;
import org.geoserver.wfs.WFSGetFeatureOutputFormat;
import org.geoserver.wfs.WFSInfo;
import org.geoserver.wfs.request.FeatureCollectionResponse;
import org.geoserver.wfs.request.GetFeatureRequest;

/**
 * Encodes features in Geographic Markup Language (GML) version 2.
 * 
 * <p>
 * GML2-GZIP format is just GML2 with gzip compression. If GML2-GZIP format was requested,
 * <code>getContentEncoding()</code> will retutn <code>"gzip"</code>, otherwise will return
 * <code>null</code>
 * </p>
 * 
 * @author Gabriel Rold?n
 * @version $Id$
 */
public class GML2OutputFormat extends WFSGetFeatureOutputFormat {
    public static final String formatName = "GML2";

    public static final String MIME_TYPE = "text/xml; subtype=gml/2.1.2";

    /**
     * GeoServer configuration
     */
    private GeoServer geoServer;

    /**
     * The catalog
     */
    protected Catalog catalog;

    /**
     * Creates the producer with a reference to the GetFeature operation using it.
     */
    public GML2OutputFormat(GeoServer geoServer) {
        super(geoServer, new HashSet(Arrays.asList(new String[] { "GML2", MIME_TYPE })));

        this.geoServer = geoServer;
        this.catalog = geoServer.getCatalog();
    }

    @Override
    public String getMimeType(Object value, Operation operation) throws ServiceException {
        return MIME_TYPE;
    }

    public String getCapabilitiesElementName() {
        return "GML2";
    }

    protected void write(FeatureCollectionResponse results, OutputStream output,
            Operation getFeature) throws IOException, ServiceException {
        GetFeatureRequest request = GetFeatureRequest.adapt(getFeature.getParameters()[0]);

        if (results == null) {
            throw new IllegalStateException("It seems prepare() has not been called"
                    + " or has not succeed");
        }

        GML2Writer writer = new GML2Writer(gs);

        WFSInfo wfs = getInfo();
        writer.setIndent(wfs.isVerbose());
        writer.setFeatureBounding(wfs.isFeatureBounding());

        if (wfs.isCanonicalSchemaLocation()) {
            writer.setSchemaLocation(wfsCanonicalSchemaLocation());
        } else {
            writer.setSchemaLocation(wfsSchemaLocation(request.getBaseUrl()));
        }
        GMLInfo gml = wfs.getGML().get(WFSInfo.Version.V_10);
        writer.setGmlPrefixing(wfs.isCiteCompliant() || !gml.getOverrideGMLAttributes());
        if (results.getLockId() != null) {
            writer.setLockId(results.getLockId());
        }
        writer.setSrsPrefix(gml.getSrsNameStyle().getPrefix());
        
        writer.write(results.getFeature(), request.getBaseUrl(), output);
    }

    protected String wfsSchemaLocation(String baseUrl) {
        return buildSchemaURL(baseUrl, "wfs/1.0.0/WFS-basic.xsd");
    }

    protected String wfsCanonicalSchemaLocation() {
        return org.geoserver.wfs.xml.v1_0_0.WFS.CANONICAL_SCHEMA_LOCATION_BASIC;
    }

}
