/* Copyright (c) 2001 - 2013 OpenPlans - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wfs.json;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import org.geoserver.config.GeoServer;
import org.geoserver.json.GeoJSONFeatureWriter;
import org.geoserver.json.JSONType;
import org.geoserver.platform.Operation;
import org.geoserver.platform.ServiceException;
import org.geoserver.wfs.WFSGetFeatureOutputFormat;
import org.geoserver.wfs.WFSInfo;
import org.geoserver.wfs.request.FeatureCollectionResponse;

/**
 * A GetFeatureInfo response handler specialized in producing Json and JsonP data for a GetFeatureInfo request.
 * 
 * @author Simone Giannecchini, GeoSolutions
 * @author Carlo Cancellieri - GeoSolutions
 * 
 */
public class GeoJSONGetFeatureResponse extends WFSGetFeatureOutputFormat {
    private final Logger LOGGER = org.geotools.util.logging.Logging.getLogger(this.getClass());

    // store the response type
    private final boolean jsonp;

    // the json feature writer
    private GeoJSONFeatureWriter writer;

    public GeoJSONGetFeatureResponse(GeoServer gs, String format) {
        super(gs, format);
        this.writer = writer;
        if (JSONType.isJsonMimeType(format)) {
            jsonp = false;
        } else if (JSONType.isJsonpMimeType(format)) {
            jsonp = true;
        } else {
            throw new IllegalArgumentException(
                    "Unable to create the JSON Response handler using format: " + format
                            + " supported mymetype are: "
                            + Arrays.toString(JSONType.getSupportedTypes()));
        }

    }

    /**
     * capabilities output format string.
     */
    public String getCapabilitiesElementName() {
        return JSONType.getJSONType(getOutputFormat()).toString();
    }

    /**
     * Returns the mime type
     */
    public String getMimeType(Object value, Operation operation) throws ServiceException {
        return getOutputFormat();
    }

    @Override
    protected void write(FeatureCollectionResponse featureCollection, OutputStream output,
            Operation operation) throws IOException {
        WFSInfo wfs = getInfo();
        boolean featureBounding = wfs.isFeatureBounding();
        List resultsList = featureCollection.getFeature();
        GeoJSONFeatureWriter writer = new GeoJSONFeatureWriter(gs);
        writer.setJsonp(jsonp);
        writer.setFeatureBounding(featureBounding);
        writer.write(resultsList, output);
    }

}
