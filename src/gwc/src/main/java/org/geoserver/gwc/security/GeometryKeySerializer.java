/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.gwc.security;

import org.geoserver.catalog.ResourcePool;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.locationtech.jts.geom.Geometry;

/**
 * Serializes {@link Geometry} parameter values to {@code "AUTHORITY:CODE:WKT"} when CRS is known, plain WKT otherwise.
 * CRS is resolved from {@link Geometry#getUserData()} (if a {@link CoordinateReferenceSystem}) via
 * {@link ResourcePool#lookupIdentifier}, then falls back to {@link Geometry#getSRID()}.
 */
class GeometryKeySerializer extends TypedKeySerializer<Geometry> {

    GeometryKeySerializer() {
        super(Geometry.class);
    }

    @Override
    public String toKey(Geometry value) {
        String wkt = value.norm().toText();
        String crsCode = resolveCrsCode(value);
        return crsCode != null ? crsCode + ":" + wkt : wkt;
    }

    private static String resolveCrsCode(Geometry geom) {
        Object userData = geom.getUserData();
        if (userData instanceof CoordinateReferenceSystem crs) {
            try {
                // fullScan=false: avoid expensive authority factory scans on tile request path
                String id = ResourcePool.lookupIdentifier(crs, false);
                if (id != null) return id;
            } catch (FactoryException e) {
                // fall through to SRID
            }
        }
        int srid = geom.getSRID();
        return srid > 0 ? "EPSG:" + srid : null;
    }
}
