/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.gwc.security;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.PublishedInfo;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.security.AccessLimits;
import org.geoserver.security.ResourceAccessManager;
import org.geoserver.security.SecureCatalogImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Thread-local cache for the security cache key computed for the current tile request.
 *
 * <p>{@link #resolveKey(PublishedInfo)} lazily computes the key on the first call within a request and caches it for
 * subsequent meta-tile sub-requests on the same thread. {@link SecurityKeyDispatcherCallback} clears the holder at the
 * end of every request.
 *
 * <p>{@code ""} (empty string) is a sentinel meaning "computed, unrestricted access".
 */
public class SecurityKeyHolder {

    // sentinel for "computed, unrestricted" — safe because buildKey() never returns "":
    // it returns null (unrestricted) or a non-empty JSON object string. "" never escapes
    // this class; resolveKey() always returns null or a real key.
    private static final String UNRESTRICTED = "";

    private static final ThreadLocal<String> KEY = new ThreadLocal<>();
    private static final ThreadLocal<String> TAGS = new ThreadLocal<>();

    private SecurityKeyHolder() {}

    /**
     * Returns the security cache key for the given layer, computing and caching it on the first call within a request.
     * Returns {@code null} for unrestricted access or when the security infrastructure is unavailable.
     */
    public static String resolveKey(PublishedInfo published) {
        if (KEY.get() != null) {
            String cached = KEY.get();
            return cached.isEmpty() ? null : cached;
        }
        AccessLimitsKeyBuilder keyBuilder = GeoServerExtensions.bean(AccessLimitsKeyBuilder.class);
        if (keyBuilder == null) {
            KEY.set(UNRESTRICTED);
            return null;
        }
        SecureCatalogImpl secureCatalog = GeoServerExtensions.bean(SecureCatalogImpl.class);
        if (secureCatalog == null) {
            KEY.set(UNRESTRICTED);
            return null;
        }
        ResourceAccessManager ram = secureCatalog.getResourceAccessManager();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String key = buildKey(published, ram, auth, keyBuilder);
        KEY.set(key != null ? key : UNRESTRICTED);
        return key;
    }

    /**
     * Returns the sorted comma-joined security tags for the given layer, computing and caching them on the first call
     * within a request. Returns {@code null} when no limits carry tags, or when security infrastructure is unavailable.
     * Only meaningful when {@link #resolveKey} returns non-null.
     */
    public static String resolveSecurityTags(PublishedInfo published) {
        if (TAGS.get() != null) {
            String cached = TAGS.get();
            return cached.isEmpty() ? null : cached;
        }
        SecureCatalogImpl secureCatalog = GeoServerExtensions.bean(SecureCatalogImpl.class);
        if (secureCatalog == null) {
            TAGS.set(UNRESTRICTED);
            return null;
        }
        ResourceAccessManager ram = secureCatalog.getResourceAccessManager();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Set<String> allTags = new TreeSet<>();
        if (published instanceof LayerInfo layer) {
            collectTags(ram.getAccessLimits(auth, layer), allTags);
        } else if (published instanceof LayerGroupInfo group) {
            for (LayerInfo l : group.layers()) collectTags(ram.getAccessLimits(auth, l), allTags);
        }
        String tags = allTags.isEmpty() ? null : String.join(",", allTags);
        TAGS.set(tags != null ? tags : UNRESTRICTED);
        return tags;
    }

    private static void collectTags(AccessLimits limits, Set<String> out) {
        if (limits != null && limits.getSecurityTags() != null) out.addAll(limits.getSecurityTags());
    }

    private static String buildKey(
            PublishedInfo published,
            ResourceAccessManager ram,
            Authentication auth,
            AccessLimitsKeyBuilder keyBuilder) {
        if (published instanceof LayerInfo layer) {
            return keyBuilder.buildKey(ram.getAccessLimits(auth, layer));
        }
        if (published instanceof LayerGroupInfo group) {
            List<LayerInfo> layers = group.layers();
            List<String> names = layers.stream().map(LayerInfo::prefixedName).toList();
            List<AccessLimits> limitsList = layers.stream()
                    .map(l -> (AccessLimits) ram.getAccessLimits(auth, l))
                    .toList();
            return keyBuilder.buildLayerGroupKey(names, limitsList);
        }
        return null;
    }

    /** Clears the cached key and tags. Called by {@link SecurityKeyDispatcherCallback} after each request. */
    public static void clear() {
        KEY.remove();
        TAGS.remove();
    }
}
