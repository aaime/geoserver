/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.gwc.security;

import java.util.List;
import java.util.Optional;
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
 * Thread-local cache for the security cache key and tags computed for the current tile request.
 *
 * <p>{@link #resolveKey(PublishedInfo)} and {@link #resolveSecurityTags(PublishedInfo)} share a single computation
 * pass: access limits are fetched once per layer, then key and tags are derived together.
 * {@link SecurityKeyDispatcherCallback} clears the holder at the end of every request.
 */
public class SecurityKeyHolder {

    private record KeyAndTags(String key, String tags) {}

    // null = not yet computed; Optional.empty() = computed, unrestricted; Optional.of(...) = restricted
    private static final ThreadLocal<Optional<KeyAndTags>> RESOLVED = new ThreadLocal<>();

    private SecurityKeyHolder() {}

    /**
     * Returns the security cache key for the given layer, computing and caching it on the first call within a request.
     * Returns {@code null} for unrestricted access or when the security infrastructure is unavailable.
     */
    public static String resolveKey(PublishedInfo published) {
        KeyAndTags knt = resolve(published);
        return knt != null ? knt.key() : null;
    }

    /**
     * Returns the sorted comma-joined security tags for the given layer, computing and caching them on the first call
     * within a request. Returns {@code null} when no limits carry tags, or when security infrastructure is unavailable.
     * Only meaningful when {@link #resolveKey} returns non-null.
     */
    public static String resolveSecurityTags(PublishedInfo published) {
        KeyAndTags knt = resolve(published);
        return knt != null ? knt.tags() : null;
    }

    private static KeyAndTags resolve(PublishedInfo published) {
        Optional<KeyAndTags> cached = RESOLVED.get();
        if (cached != null) return cached.orElse(null);

        AccessLimitsKeyBuilder keyBuilder = GeoServerExtensions.bean(AccessLimitsKeyBuilder.class);
        if (keyBuilder == null) {
            RESOLVED.set(Optional.empty());
            return null;
        }
        SecureCatalogImpl secureCatalog = GeoServerExtensions.bean(SecureCatalogImpl.class);
        if (secureCatalog == null) {
            RESOLVED.set(Optional.empty());
            return null;
        }
        ResourceAccessManager ram = secureCatalog.getResourceAccessManager();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        KeyAndTags result = buildKeyAndTags(published, ram, auth, keyBuilder);
        RESOLVED.set(Optional.ofNullable(result));
        return result;
    }

    private static KeyAndTags buildKeyAndTags(
            PublishedInfo published,
            ResourceAccessManager ram,
            Authentication auth,
            AccessLimitsKeyBuilder keyBuilder) {
        if (published instanceof LayerInfo layer) {
            AccessLimits limits = ram.getAccessLimits(auth, layer);
            String key = keyBuilder.buildKey(limits);
            String tags = tagsFrom(limits);
            return (key != null || tags != null) ? new KeyAndTags(key, tags) : null;
        }
        if (published instanceof LayerGroupInfo group) {
            List<LayerInfo> layers = group.layers();
            List<String> names = layers.stream().map(LayerInfo::prefixedName).toList();
            List<AccessLimits> limitsList = layers.stream()
                    .map(l -> (AccessLimits) ram.getAccessLimits(auth, l))
                    .toList();
            String key = keyBuilder.buildLayerGroupKey(names, limitsList);
            Set<String> allTags = new TreeSet<>();
            for (AccessLimits l : limitsList) collectTags(l, allTags);
            String tags = allTags.isEmpty() ? null : String.join(",", allTags);
            return (key != null || tags != null) ? new KeyAndTags(key, tags) : null;
        }
        return null;
    }

    private static String tagsFrom(AccessLimits limits) {
        if (limits == null || limits.getSecurityTags() == null) return null;
        Set<String> sorted = new TreeSet<>(limits.getSecurityTags());
        return sorted.isEmpty() ? null : String.join(",", sorted);
    }

    private static void collectTags(AccessLimits limits, Set<String> out) {
        if (limits != null && limits.getSecurityTags() != null) out.addAll(limits.getSecurityTags());
    }

    /** Clears the cached key and tags. Called by {@link SecurityKeyDispatcherCallback} after each request. */
    public static void clear() {
        RESOLVED.remove();
    }
}
