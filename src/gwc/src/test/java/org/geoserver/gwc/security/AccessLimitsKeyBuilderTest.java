/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.gwc.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.geoserver.security.AccessLimits;
import org.geoserver.security.CatalogMode;
import org.geoserver.security.CoverageAccessLimits;
import org.geoserver.security.DataAccessLimits;
import org.geoserver.security.VectorAccessLimits;
import org.geotools.api.filter.Filter;
import org.geotools.api.filter.FilterFactory;
import org.geotools.api.filter.expression.PropertyName;
import org.geotools.api.parameter.GeneralParameterValue;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.filter.text.ecql.ECQL;
import org.geotools.gce.imagemosaic.ImageMosaicFormat;
import org.geotools.parameter.DefaultParameterDescriptor;
import org.geotools.parameter.Parameter;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;

public class AccessLimitsKeyBuilderTest {

    static final GeometryFactory GF = new GeometryFactory();
    static final FilterFactory FF = CommonFactoryFinder.getFilterFactory();

    AccessLimitsKeyBuilder builder;
    IgnorableParameterRegistry ignorable;

    @Before
    public void setUp() {
        ignorable = new IgnorableParameterRegistry();
        builder = new AccessLimitsKeyBuilder(List.of(), ignorable);
    }

    static MultiPolygon triangle() {
        Polygon tri = GF.createPolygon(
                new Coordinate[] {new Coordinate(0, 0), new Coordinate(0, 1), new Coordinate(1, 1), new Coordinate(0, 0)
                });
        return GF.createMultiPolygon(new Polygon[] {tri});
    }

    // --- null / unrestricted ---

    @Test
    public void testNullReturnsNull() {
        assertNull(builder.buildKey(null));
    }

    @Test
    public void testBaseAccessLimitsReturnsNull() {
        assertNull(builder.buildKey(new AccessLimits(CatalogMode.HIDE)));
    }

    @Test
    public void testIncludeFilterReturnsNull() {
        assertNull(builder.buildKey(new DataAccessLimits(CatalogMode.HIDE, Filter.INCLUDE)));
    }

    @Test
    public void testEmptyVectorLimitsReturnsNull() {
        VectorAccessLimits v = new VectorAccessLimits(CatalogMode.HIDE, null, Filter.INCLUDE, null, Filter.INCLUDE);
        assertNull(builder.buildKey(v));
    }

    // --- DataAccessLimits ---

    @Test
    public void testDataFilter() throws Exception {
        DataAccessLimits d = new DataAccessLimits(CatalogMode.HIDE, ECQL.toFilter("population > 1000"));
        String key = builder.buildKey(d);
        assertNotNull(key);
        assertTrue(key.contains("readFilter"));
        assertTrue(key.contains("population"));
    }

    // --- VectorAccessLimits ---

    @Test
    public void testVectorAttributes() {
        List<PropertyName> attrs = List.of(FF.property("name"), FF.property("pop"));
        VectorAccessLimits v = new VectorAccessLimits(CatalogMode.HIDE, attrs, Filter.INCLUDE, null, Filter.INCLUDE);
        String key = builder.buildKey(v);
        assertNotNull(key);
        assertTrue(key.contains("readAttributes"));
        // sorted: name,pop
        assertTrue(key.contains("name,pop"));
    }

    @Test
    public void testVectorAttributesSorted() {
        List<PropertyName> ab = List.of(FF.property("a"), FF.property("b"));
        List<PropertyName> ba = List.of(FF.property("b"), FF.property("a"));
        VectorAccessLimits va = new VectorAccessLimits(CatalogMode.HIDE, ab, Filter.INCLUDE, null, Filter.INCLUDE);
        VectorAccessLimits vb = new VectorAccessLimits(CatalogMode.HIDE, ba, Filter.INCLUDE, null, Filter.INCLUDE);
        assertEquals(builder.buildKey(va), builder.buildKey(vb));
    }

    @Test
    public void testVectorClip() {
        VectorAccessLimits v =
                new VectorAccessLimits(CatalogMode.HIDE, null, Filter.INCLUDE, null, Filter.INCLUDE, triangle());
        String key = builder.buildKey(v);
        assertNotNull(key);
        assertTrue(key.contains("clipVectorFilter"));
    }

    @Test
    public void testVectorIntersect() {
        VectorAccessLimits v = new VectorAccessLimits(CatalogMode.HIDE, null, Filter.INCLUDE, null, Filter.INCLUDE);
        v.setIntersectVectorFilter(GF.createPoint(new Coordinate(5, 5)));
        String key = builder.buildKey(v);
        assertNotNull(key);
        assertTrue(key.contains("intersectVectorFilter"));
    }

    // --- CoverageAccessLimits ---

    @Test
    public void testCoverageRasterFilter() {
        CoverageAccessLimits c = new CoverageAccessLimits(CatalogMode.HIDE, Filter.INCLUDE, triangle(), null);
        String key = builder.buildKey(c);
        assertNotNull(key);
        assertTrue(key.contains("rasterFilter"));
    }

    @Test
    public void testCoverageIgnorableParam() {
        Parameter<Boolean> mt = new Parameter<>(ImageMosaicFormat.ALLOW_MULTITHREADING, true);
        CoverageAccessLimits c =
                new CoverageAccessLimits(CatalogMode.HIDE, Filter.INCLUDE, null, new GeneralParameterValue[] {mt});
        // ignorable param contributes nothing → unrestricted
        assertNull(builder.buildKey(c));
    }

    @Test
    public void testCoverageParam() {
        DefaultParameterDescriptor<String> desc = new DefaultParameterDescriptor<>("BANDS", String.class, null, null);
        Parameter<String> bands = new Parameter<>(desc, "1,2,3");
        CoverageAccessLimits c =
                new CoverageAccessLimits(CatalogMode.HIDE, Filter.INCLUDE, null, new GeneralParameterValue[] {bands});
        String key = builder.buildKey(c);
        assertNotNull(key);
        assertTrue(key.contains("BANDS"));
        assertTrue(key.contains("1,2,3"));
    }

    @Test
    public void testCoverageUnknownParamThrows() {
        DefaultParameterDescriptor<double[]> desc =
                new DefaultParameterDescriptor<>("MyArray", double[].class, null, null);
        Parameter<double[]> bad = new Parameter<>(desc, new double[] {1.0, 2.0});
        CoverageAccessLimits c =
                new CoverageAccessLimits(CatalogMode.HIDE, Filter.INCLUDE, null, new GeneralParameterValue[] {bad});
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> builder.buildKey(c));
        assertTrue(ex.getMessage().contains("MyArray"));
        assertTrue(ex.getMessage().contains(IgnorableParameterRegistry.SYSTEM_PROPERTY));
    }

    // --- layer group ---

    @Test
    public void testLayerGroupAllUnrestricted() throws Exception {
        List<String> names = List.of("ws:a", "ws:b");
        List<AccessLimits> limits = List.of(
                new DataAccessLimits(CatalogMode.HIDE, Filter.INCLUDE),
                new DataAccessLimits(CatalogMode.HIDE, Filter.INCLUDE));
        assertNull(builder.buildLayerGroupKey(names, limits));
    }

    @Test
    public void testLayerGroupPartialRestriction() throws Exception {
        DataAccessLimits restricted = new DataAccessLimits(CatalogMode.HIDE, ECQL.toFilter("population > 0"));
        DataAccessLimits open = new DataAccessLimits(CatalogMode.HIDE, Filter.INCLUDE);
        String key = builder.buildLayerGroupKey(List.of("ws:a", "ws:b"), List.of(restricted, open));
        assertNotNull(key);
        assertTrue(key.startsWith("["));
        assertTrue(key.contains("ws:a"));
        assertTrue(key.contains("ws:b"));
        assertTrue(key.contains("readFilter"));
    }

    @Test
    public void testLayerGroupSizeMismatch() {
        assertThrows(IllegalArgumentException.class, () -> builder.buildLayerGroupKey(List.of("a"), List.of()));
    }

    // --- custom serializer ---

    @Test
    public void testCustomSerializerPriority() {
        ParameterValueKeySerializer<String> upper = new TypedKeySerializer<>(String.class) {
            @Override
            public String toKey(String value) {
                return value.toUpperCase();
            }
        };
        AccessLimitsKeyBuilder b2 = new AccessLimitsKeyBuilder(List.of(upper), ignorable);
        DefaultParameterDescriptor<String> desc = new DefaultParameterDescriptor<>("TAG", String.class, null, null);
        Parameter<String> tag = new Parameter<>(desc, "hello");
        CoverageAccessLimits c =
                new CoverageAccessLimits(CatalogMode.HIDE, Filter.INCLUDE, null, new GeneralParameterValue[] {tag});
        String key = b2.buildKey(c);
        assertNotNull(key);
        assertTrue("custom serializer must uppercase", key.contains("HELLO"));
    }

    @Test
    public void testDuplicateCustomSerializerFails() {
        ParameterValueKeySerializer<String> s1 = new TypedKeySerializer<>(String.class);
        ParameterValueKeySerializer<String> s2 = new TypedKeySerializer<>(String.class);
        assertThrows(IllegalStateException.class, () -> new AccessLimitsKeyBuilder(List.of(s1, s2), ignorable));
    }

    // --- filter normalization ---

    @Test
    public void testFilterNormalization() throws Exception {
        // literal-left swap must produce same key
        DataAccessLimits a = new DataAccessLimits(CatalogMode.HIDE, ECQL.toFilter("13 = population"));
        DataAccessLimits b = new DataAccessLimits(CatalogMode.HIDE, ECQL.toFilter("population = 13"));
        assertEquals(builder.buildKey(a), builder.buildKey(b));
    }
}
