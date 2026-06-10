/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.gwc.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.geoserver.security.AccessLimits;
import org.geoserver.security.CoverageAccessLimits;
import org.geoserver.security.DataAccessLimits;
import org.geoserver.security.VectorAccessLimits;
import org.geotools.api.filter.Filter;
import org.geotools.api.filter.expression.PropertyName;
import org.geotools.api.parameter.GeneralParameterValue;
import org.geotools.api.parameter.ParameterValue;
import org.geotools.util.Range;
import org.locationtech.jts.geom.Geometry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Builds a stable, human-readable JSON cache key from an {@link AccessLimits} instance.
 *
 * <p>Returns {@code null} when access limits impose no content restrictions (unrestricted access), indicating that no
 * security cache dimension should be added to the tile key.
 *
 * <p>Custom {@link ParameterValueKeySerializer} beans are consulted before built-ins and take priority. Duplicate
 * contributed serializers for the same value type fail at construction.
 *
 * <p>Unknown {@link GeneralParameterValue} types that are neither ignorable (see {@link IgnorableParameterRegistry})
 * nor serializable cause a request-time failure with a message naming the descriptor and pointing to the available
 * extension mechanisms.
 *
 * <p>Keys longer than {@value #DEFAULT_MAX_KEY_LENGTH} characters (configurable via the
 * {@value #MAX_KEY_LENGTH_PROPERTY} system property) are trimmed by truncating the longest individual field values
 * first, replacing each with its prefix followed by {@code "...too long, sha is <sha256hex>"}. This keeps every field
 * visible in the stored property file while bounding total size. The default (64 KB) was validated to open instantly in
 * standard text editors and gives ~2,100 readable characters per geometry field in a 30-layer group.
 */
public class AccessLimitsKeyBuilder {

    static final String MAX_KEY_LENGTH_PROPERTY = "gwc.security.maxKeyLength";
    static final int DEFAULT_MAX_KEY_LENGTH = 65536;

    private static final String TRUNCATION_SUFFIX = "...too long, sha is ";
    // TRUNCATION_SUFFIX (20) + sha256 hex (64) = 84
    private static final int SUFFIX_FIXED_LENGTH = TRUNCATION_SUFFIX.length() + 64;
    // minimum chars of the original value kept visible after truncation
    private static final int MIN_FIELD_PREFIX = 50;

    private static final JsonMapper MAPPER = new JsonMapper();

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern(
                    "yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.ROOT)
            .withZone(ZoneOffset.UTC);
    private static final FilterKeySerializer FILTER_SER = new FilterKeySerializer();
    private static final GeometryKeySerializer GEOM_SER = new GeometryKeySerializer();

    /**
     * Built-in serializers, checked after custom ones. Order matters: specific before broad. Note: List and Range are
     * handled in serializeValue() directly since they need to recurse into the serializer chain.
     */
    private static final List<ParameterValueKeySerializer<?>> BUILT_INS = List.of(
            FILTER_SER,
            GEOM_SER,
            new TypedKeySerializer<>(Date.class) {
                @Override
                public String toKey(Date value) {
                    return DATE_FORMAT.format(value.toInstant());
                }
            },
            new TypedKeySerializer<>(Number.class),
            new TypedKeySerializer<>(Boolean.class),
            new TypedKeySerializer<>(String.class));

    private final List<ParameterValueKeySerializer<?>> custom;
    private final IgnorableParameterRegistry ignorable;
    private final int maxKeyLength;

    public AccessLimitsKeyBuilder(List<ParameterValueKeySerializer<?>> custom, IgnorableParameterRegistry ignorable) {
        this(custom, ignorable, Integer.getInteger(MAX_KEY_LENGTH_PROPERTY, DEFAULT_MAX_KEY_LENGTH));
    }

    AccessLimitsKeyBuilder(
            List<ParameterValueKeySerializer<?>> custom, IgnorableParameterRegistry ignorable, int maxKeyLength) {
        // fail fast on duplicate contributed serializers for the same value type
        Map<Class<?>, String> seen = new LinkedHashMap<>();
        for (ParameterValueKeySerializer<?> s : custom) {
            String prev = seen.put(s.getValueType(), s.getClass().getName());
            if (prev != null) {
                throw new IllegalStateException(errorDuplicateSerializer(s.getValueType(), prev, s.getClass()));
            }
        }
        this.custom = List.copyOf(custom);
        this.ignorable = ignorable;
        this.maxKeyLength = maxKeyLength;
    }

    /**
     * Builds a cache key for a single layer. Returns {@code null} for unrestricted access (no key injection needed).
     */
    public String buildKey(AccessLimits limits) {
        ObjectNode node = buildKeyNode(limits);
        if (node == null || node.isEmpty()) return null;
        return buildAndLimit(node);
    }

    private ObjectNode buildKeyNode(AccessLimits limits) {
        if (limits == null) return null;
        if (limits instanceof VectorAccessLimits val) return buildVectorNode(val);
        if (limits instanceof CoverageAccessLimits cal) return buildCoverageNode(cal);
        if (limits instanceof DataAccessLimits dal) return buildDataNode(dal);
        return null; // base AccessLimits has no tile content affecting fields
    }

    private ObjectNode buildVectorNode(VectorAccessLimits limits) {
        ObjectNode node = MAPPER.createObjectNode();
        addReadFilter(node, limits.getReadFilter());
        List<PropertyName> attrs = limits.getReadAttributes();
        if (attrs != null && !attrs.isEmpty()) {
            node.put(
                    "readAttributes",
                    attrs.stream().map(PropertyName::getPropertyName).sorted().collect(Collectors.joining(",")));
        }
        addGeometry(node, "clipVectorFilter", limits.getClipVectorFilter());
        addGeometry(node, "intersectVectorFilter", limits.getIntersectVectorFilter());
        return node;
    }

    private static void addReadFilter(ObjectNode node, Filter filter) {
        if (filter != null && !Filter.INCLUDE.equals(filter)) {
            node.put("readFilter", FILTER_SER.toKey(filter));
        }
    }

    private static void addGeometry(ObjectNode node, String field, Geometry geom) {
        if (geom != null) {
            node.put(field, GEOM_SER.toKey(geom));
        }
    }

    private ObjectNode buildCoverageNode(CoverageAccessLimits limits) {
        ObjectNode node = MAPPER.createObjectNode();
        addReadFilter(node, limits.getReadFilter());
        addGeometry(node, "rasterFilter", limits.getRasterFilter());
        GeneralParameterValue[] params = limits.getParams();
        if (params != null) {
            for (GeneralParameterValue gpv : params) {
                if (ignorable.isIgnorable(gpv)) continue;
                String code = gpv.getDescriptor().getName().getCode();
                if (!(gpv instanceof ParameterValue<?> pv)) {
                    throw new IllegalArgumentException(errorUnknownParam(code, gpv));
                }
                Object value = pv.getValue();
                if (value == null) continue;
                node.put(code, serializeValue(code, value));
            }
        }
        return node;
    }

    @SuppressWarnings("unchecked")
    private String serializeValue(String paramName, Object value) {
        // List and Range recurse into this method, so they can't be ParameterValueKeySerializer instances
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(e -> serializeValue(paramName, e))
                    .sorted()
                    .collect(Collectors.joining(","));
        }
        if (value instanceof Range<?> range) {
            String min = range.getMinValue() != null ? serializeValue(paramName, range.getMinValue()) : "*";
            String max = range.getMaxValue() != null ? serializeValue(paramName, range.getMaxValue()) : "*";
            return min + "/" + max;
        }
        // custom serializers take priority over built-ins
        for (ParameterValueKeySerializer<?> s : custom) {
            if (s.getValueType().isAssignableFrom(value.getClass())) {
                return ((ParameterValueKeySerializer<Object>) s).toKey(value);
            }
        }
        for (ParameterValueKeySerializer<?> s : BUILT_INS) {
            if (s.getValueType().isAssignableFrom(value.getClass())) {
                return ((ParameterValueKeySerializer<Object>) s).toKey(value);
            }
        }
        throw new IllegalArgumentException(errorUnknownValue(paramName, value));
    }

    private ObjectNode buildDataNode(DataAccessLimits limits) {
        ObjectNode node = MAPPER.createObjectNode();
        addReadFilter(node, limits.getReadFilter());
        return node;
    }

    /**
     * Builds a composite cache key for a layer group. Layer names and limits must be in composition order. Returns
     * {@code null} if no constituent has content-affecting restrictions.
     */
    public String buildLayerGroupKey(List<String> layerNames, List<AccessLimits> limits) {
        if (layerNames.size() != limits.size()) {
            throw new IllegalArgumentException("layerNames and limits must have the same size");
        }
        boolean anyRestricted = false;
        ArrayNode array = MAPPER.createArrayNode();
        for (int i = 0; i < layerNames.size(); i++) {
            ObjectNode layerNode = buildKeyNode(limits.get(i));
            // each entry always has "layer" first, then restriction fields (if any)
            ObjectNode entry = MAPPER.createObjectNode();
            entry.put("layer", layerNames.get(i));
            if (layerNode != null && !layerNode.isEmpty()) {
                anyRestricted = true;
                for (String name : layerNode.propertyNames()) {
                    entry.set(name, layerNode.get(name));
                }
            }
            array.add(entry);
        }
        if (!anyRestricted) return null;
        return buildAndLimit(array);
    }

    private String buildAndLimit(ObjectNode node) {
        String s = node.toString();
        if (s.length() <= maxKeyLength) return s;
        truncateLongestFields(List.of(node), s.length());
        return node.toString();
    }

    private String buildAndLimit(ArrayNode array) {
        String s = array.toString();
        if (s.length() <= maxKeyLength) return s;
        List<ObjectNode> entries = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            if (array.get(i) instanceof ObjectNode on) entries.add(on);
        }
        truncateLongestFields(entries, s.length());
        return array.toString();
    }

    private void truncateLongestFields(List<ObjectNode> nodes, int currentLength) {
        record Field(ObjectNode node, String name, String value) {}
        List<Field> fields = new ArrayList<>();
        for (ObjectNode n : nodes) {
            for (String fname : n.propertyNames()) {
                JsonNode v = n.get(fname);
                if (v.isTextual()) fields.add(new Field(n, fname, v.textValue()));
            }
        }
        // longest value first — each truncation removes the most bytes
        fields.sort(Comparator.comparingInt(f -> -f.value().length()));

        int current = currentLength;
        for (Field f : fields) {
            if (current <= maxKeyLength) break;
            String orig = f.value();
            int length = orig.length();
            // truncation only helps if the result is shorter than the original
            if (length <= MIN_FIELD_PREFIX + SUFFIX_FIXED_LENGTH) continue;
            int excess = current - maxKeyLength;
            // keep as much prefix as possible while achieving at least `excess` reduction;
            // fall back to MIN_FIELD_PREFIX when the ideal prefix would be too small
            int prefix = Math.max(MIN_FIELD_PREFIX, length - SUFFIX_FIXED_LENGTH - excess);
            if (prefix + SUFFIX_FIXED_LENGTH >= length) continue; // no actual savings
            String truncated = orig.substring(0, prefix) + TRUNCATION_SUFFIX + sha256hex(orig);
            f.node().put(f.name(), truncated);
            current -= length - truncated.length();
        }
    }

    private static String sha256hex(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String errorDuplicateSerializer(Class<?> valueType, String first, Class<?> second) {
        return "Duplicate ParameterValueKeySerializer for value type %s: %s and %s"
                .formatted(valueType.getName(), first, second.getName());
    }

    private static String errorUnknownParam(String code, GeneralParameterValue gpv) {
        return "Cannot build security cache key for parameter '%s': GeneralParameterValue type %s is not a ParameterValue and has no contributed serializer. Contribute a ParameterValueKeySerializer bean or add '%s' to the %s system property if it does not affect tile content."
                .formatted(code, gpv.getClass().getName(), code, IgnorableParameterRegistry.SYSTEM_PROPERTY);
    }

    private static String errorUnknownValue(String paramName, Object value) {
        return "Cannot build security cache key for parameter '%s': no ParameterValueKeySerializer found for value type %s. Contribute a ParameterValueKeySerializer bean or add '%s' to the %s system property if it does not affect tile content."
                .formatted(
                        paramName, value.getClass().getName(), paramName, IgnorableParameterRegistry.SYSTEM_PROPERTY);
    }
}
