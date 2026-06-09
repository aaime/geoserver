/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.gwc.security;

/**
 * Base implementation of {@link ParameterValueKeySerializer} that holds the value type binding. Relies on the interface
 * default {@code toKey} ({@code value.toString()}); subclasses override when needed.
 */
class TypedKeySerializer<T> implements ParameterValueKeySerializer<T> {

    private final Class<T> valueType;

    TypedKeySerializer(Class<T> valueType) {
        this.valueType = valueType;
    }

    @Override
    public Class<T> getValueType() {
        return valueType;
    }
}
