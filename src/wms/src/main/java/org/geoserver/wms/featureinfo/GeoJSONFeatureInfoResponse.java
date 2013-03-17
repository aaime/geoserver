/* Copyright (c) 2001 - 2013 OpenPlans - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wms.featureinfo;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import net.opengis.wfs.FeatureCollectionType;

import org.geoserver.json.GeoJSONFeatureWriter;
import org.geoserver.json.JSONType;
import org.geoserver.wms.GetFeatureInfoRequest;
import org.geoserver.wms.WMS;

/**
 * A GetFeatureInfo response handler specialized in producing Json and JsonP data for a GetFeatureInfo request.
 * 
 * @author Simone Giannecchini, GeoSolutions
 * @author Carlo Cancellieri - GeoSolutions
 * 
 */
public class GeoJSONFeatureInfoResponse extends GetFeatureInfoOutputFormat {

    protected final WMS wms;

    /**
     * @param wms
     * @param outputFormat
     * @throws Exception if outputFormat is not a valid json mime type
     */
    public GeoJSONFeatureInfoResponse(final WMS wms, final String outputFormat) throws Exception {
        super(outputFormat);
        this.wms = wms;
    }

    /**
     * Writes a Json (or Jsonp) response on the passed output stream
     * 
     * @see {@link GetFeatureInfoOutputFormat#write(FeatureCollectionType, GetFeatureInfoRequest, OutputStream)}
     */
    @Override
    public void write(FeatureCollectionType features, GetFeatureInfoRequest fInfoReq,
            OutputStream out) throws IOException {
        List resultsList = features.getFeature();
        boolean jsonp = JSONType.isJsonpMimeType(getContentType());
        GeoJSONFeatureWriter writer = new GeoJSONFeatureWriter(wms.getGeoServer());
        writer.setJsonp(jsonp);
        writer.setFeatureBounding(true);
        writer.write(resultsList, out); 
    }

}
