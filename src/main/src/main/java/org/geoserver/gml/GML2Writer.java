/*
 * Copyright (c) 2001 - 2013 OpenPlans - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.gml;

import static org.geoserver.ows.util.ResponseUtils.*;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.transform.TransformerException;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.config.GeoServer;
import org.geoserver.config.GeoServerInfo;
import org.geoserver.config.SettingsInfo;
import org.geoserver.ows.URLMangler.URLType;
import org.geoserver.platform.ServiceException;
import org.geotools.feature.FeatureCollection;
import org.geotools.gml.producer.FeatureTransformer;
import org.geotools.gml.producer.FeatureTransformer.FeatureTypeNamespaces;
import org.geotools.referencing.CRS;
import org.geotools.wfs.WFS;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

/**
 * A configurable GML2 writer
 */
public class GML2Writer {
    
    private static final int NO_FORMATTING = -1;

    private static final int INDENT_SIZE = 2;

    private GeoServer geoServer;

    private Catalog catalog;

    boolean indent = false;

    boolean featureBounding = true;

    String schemaLocation = "http://schemas.opengis.net/wfs/1.0.0/WFS-basic.xsd";

    boolean gmlPrefixing = true;

    String lockId = null;

    String srsPrefix = "EPSG:";

    public GML2Writer(GeoServer geoServer) {
        this.geoServer = geoServer;
        this.catalog = geoServer.getCatalog();
    }
    
    public boolean isIndent() {
        return indent;
    }

    public void setIndent(boolean indent) {
        this.indent = indent;
    }

    public boolean isFeatureBounding() {
        return featureBounding;
    }

    public void setFeatureBounding(boolean featureBounding) {
        this.featureBounding = featureBounding;
    }

    public String getSchemaLocation() {
        return schemaLocation;
    }

    public void setSchemaLocation(String schemaLocation) {
        this.schemaLocation = schemaLocation;
    }

    public boolean isGmlPrefixing() {
        return gmlPrefixing;
    }

    public void setGmlPrefixing(boolean gmlPrefixing) {
        this.gmlPrefixing = gmlPrefixing;
    }

    public String getLockId() {
        return lockId;
    }

    public void setLockId(String lockId) {
        this.lockId = lockId;
    }

    public String getSrsPrefix() {
        return srsPrefix;
    }

    public void setSrsPrefix(String srsPrefix) {
        this.srsPrefix = srsPrefix;
    }

    public void write(List featureCollections, String baseUrl, OutputStream output) throws IOException {
        FeatureTransformer transformer = new FeatureTransformer();
        FeatureTypeNamespaces ftNames = transformer.getFeatureTypeNamespaces();
        Map ftNamespaces = new HashMap();

        // TODO: the srs is a back, it only will work property when there is
        // one type, we really need to set it on the feature level
        int srs = -1;
        int numDecimals = -1;
        for (int i = 0; i < featureCollections.size(); i++) {
            // FeatureResults features = (FeatureResults) f.next();
            FeatureCollection features = (FeatureCollection) featureCollections.get(i);
            SimpleFeatureType featureType = (SimpleFeatureType) features.getSchema();

            ResourceInfo meta = catalog
                    .getResourceByName(featureType.getName(), ResourceInfo.class);

            String prefix = meta.getNamespace().getPrefix();
            String uri = meta.getNamespace().getURI();

            ftNames.declareNamespace(features.getSchema(), prefix, uri);

            if (ftNamespaces.containsKey(uri)) {
                String location = (String) ftNamespaces.get(uri);
                ftNamespaces.put(uri, location + "," + urlEncode(meta.getPrefixedName()));
            } else {
                // don't blindly assume it's a feature type, this class is used also by WMS
                // FeatureInfo
                // meaning it might be a coverage or a remote wms layer
                if (meta instanceof FeatureTypeInfo) {
                    String location = typeSchemaLocation(geoServer.getGlobal(),
                            (FeatureTypeInfo) meta, baseUrl);
                    ftNamespaces.put(uri, location);
                }
            }

            CoordinateReferenceSystem crs = features.getSchema().getCoordinateReferenceSystem();
            if (crs != null) {
                try {
                    Integer epsgCode = CRS.lookupEpsgCode(crs, false);
                    if (epsgCode != null) {
                        srs = epsgCode;
                    }
                } catch (Exception e) {
                    throw new ServiceException(e);
                }
            }

            // track num decimals, in cases where the query has multiple types we choose the max
            // of all the values (same deal as above, might not be a vector due to GetFeatureInfo
            // reusing this)
            if (meta instanceof FeatureTypeInfo) {
                int ftiDecimals = ((FeatureTypeInfo) meta).getNumDecimals();
                if (ftiDecimals > 0) {
                    numDecimals = numDecimals == -1 ? ftiDecimals : Math.max(numDecimals,
                            ftiDecimals);
                }
            }
        }

        SettingsInfo settings = geoServer.getSettings();

        if (numDecimals == -1) {
            numDecimals = settings.getNumDecimals();
        }

        transformer.setIndentation(indent ? INDENT_SIZE : (NO_FORMATTING));
        transformer.setNumDecimals(numDecimals);
        transformer.setFeatureBounding(featureBounding);
        transformer.setCollectionBounding(featureBounding);
        transformer.setEncoding(Charset.forName(settings.getCharset()));
        transformer.addSchemaLocation(WFS.NAMESPACE, schemaLocation);
        transformer.setGmlPrefixing(gmlPrefixing);
        transformer.setLockId(lockId);
        for (Iterator it = ftNamespaces.keySet().iterator(); it.hasNext();) {
            String uri = (String) it.next();
            transformer.addSchemaLocation(uri, (String) ftNamespaces.get(uri));
        }

        if (srs != -1) {
            transformer.setSrsName(srsPrefix + srs);
        }

        FeatureCollection[] featureResults = (FeatureCollection[]) featureCollections
                .toArray(new FeatureCollection[featureCollections.size()]);

        try {
            transformer.transform(featureResults, output);
        } catch (TransformerException gmlException) {
            String msg = " error:" + gmlException.getMessage();
            throw new ServiceException(msg, gmlException);
        }
    }
    
    protected String typeSchemaLocation(GeoServerInfo global, FeatureTypeInfo meta, String baseUrl) {
        Map<String, String> params = params("service", "WFS", "version", "1.0.0", 
                "request", "DescribeFeatureType", "typeName", meta.getPrefixedName());
        return buildURL(baseUrl, "wfs", params, URLType.SERVICE);
    }

    

}
