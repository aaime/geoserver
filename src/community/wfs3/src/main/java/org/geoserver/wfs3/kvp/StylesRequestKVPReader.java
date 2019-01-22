/*
 * (c) 2018 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 *
 */
package org.geoserver.wfs3.kvp;

import org.geoserver.wfs3.StylesRequest;

/** Parses a "styles" request */
public class StylesRequestKVPReader extends BaseKvpRequestReader {

    public StylesRequestKVPReader() {
        super(StylesRequest.class);
    }
}
