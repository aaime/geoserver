/*
 * (c) 2018 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 *
 */
package org.geoserver.wfs3.kvp;

import org.geoserver.wfs3.StyleRequest;
import org.geoserver.wfs3.StylesRequest;

/** Parses a "style" request (single style) */
public class StyleRequestKVPReader extends BaseKvpRequestReader {

    public StyleRequestKVPReader() {
        super(StyleRequest.class);
    }
}
