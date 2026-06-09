/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.gwc.security;

import org.geotools.api.filter.Filter;
import org.geotools.filter.text.ecql.ECQL;

/** Serializes {@link Filter} parameter values to normalized ECQL. */
class FilterKeySerializer extends TypedKeySerializer<Filter> {

    FilterKeySerializer() {
        super(Filter.class);
    }

    @Override
    public String toKey(Filter value) {
        // new instance per call — SimplifyingFilterVisitor has mutable state
        return ECQL.toCQL((Filter) value.accept(new NormalizingFilterVisitor(), null));
    }
}
