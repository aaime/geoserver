/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.security;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/**
 * Base class for all AccessLimits declared by a {@link ResourceAccessManager}.
 *
 * <p>AccessLimits are used to limit the access to a resource (workspace, layer, style, catalog * resource). While the
 * hierarchy of access limits has well known classes matching the associated resource, a ResourceAccessManager can also
 * create subclasses of them, thus, if any customization to access limits is needed, one can clone the
 * {@link AccessLimits} object and change its settings. For this purpose, AccessLimits implements {@link Cloneable}.
 *
 * @author Andrea Aime - GeoSolutions
 */
public class AccessLimits implements Serializable, Cloneable {
    @Serial
    private static final long serialVersionUID = 8521276966116962954L;

    /** Gets the catalog mode for this layer */
    CatalogMode mode;

    /**
     * Optional tags used for targeted cache invalidation (tile caches, CDNs, WMS caches, etc.). Not part of the content
     * fingerprint — excluded from equals/hashCode. Null by default; {@link ResourceAccessManager} implementations that
     * support tag-based invalidation may populate this with opaque identifiers representing the security rules that
     * shaped these limits (e.g. rule IDs, role names), allowing downstream caches to invalidate only the entries
     * affected by a specific rule change rather than sweeping all security-keyed entries for a layer.
     */
    private Set<String> securityTags;

    /** Builds a generic AccessLimits */
    public AccessLimits(CatalogMode mode) {
        this.mode = mode;
    }

    /** The catalog mode for this layer */
    public CatalogMode getMode() {
        return mode;
    }

    /** Tags used for targeted cache invalidation, or null if none. See {@link #securityTags}. */
    public Set<String> getSecurityTags() {
        return securityTags;
    }

    /** Sets tags used for targeted cache invalidation. See {@link #securityTags}. */
    public void setSecurityTags(Set<String> securityTags) {
        this.securityTags = securityTags == null ? null : Set.copyOf(securityTags);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((mode == null) ? 0 : mode.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        AccessLimits other = (AccessLimits) obj;
        if (mode == null) {
            if (other.mode != null) return false;
        } else if (!mode.equals(other.mode)) return false;
        return true;
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }
}
