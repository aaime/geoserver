/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.gwc.security;

import org.geoserver.ows.AbstractDispatcherCallback;
import org.geoserver.ows.Request;

/**
 * Clears the {@link SecurityKeyHolder} thread-local after each request.
 *
 * <p>The security cache key itself is computed lazily by
 * {@link org.geoserver.gwc.layer.GeoServerTileLayer#getModifiableParameters} the first time GWC needs to resolve tile
 * parameters for the current request, regardless of which protocol (WMS, WMTS, TMS, WMS-C) triggered the tile lookup.
 */
public class SecurityKeyDispatcherCallback extends AbstractDispatcherCallback {

    @Override
    public void finished(Request request) {
        SecurityKeyHolder.clear();
    }
}
