/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.gwc.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.util.Date;
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
import org.geotools.util.DateRange;
import org.geotools.util.NumberRange;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;

public class AccessLimitsKeyBuilderTest {

    static final GeometryFactory GF = new GeometryFactory();
    static final FilterFactory FF = CommonFactoryFinder.getFilterFactory();

    // expected key strings are load-bearing: any change breaks existing tile caches
    static final String TRIANGLE_WKT = "MULTIPOLYGON (((0 0, 0 1, 1 1, 0 0)))";

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

    @Test
    public void testDataFilter() throws Exception {
        DataAccessLimits d = new DataAccessLimits(CatalogMode.HIDE, ECQL.toFilter("population > 1000"));
        assertEquals("{\"readFilter\":\"population > 1000\"}", builder.buildKey(d));
    }

    @Test
    public void testVectorAttributes() {
        List<PropertyName> attrs = List.of(FF.property("name"), FF.property("pop"));
        VectorAccessLimits v = new VectorAccessLimits(CatalogMode.HIDE, attrs, Filter.INCLUDE, null, Filter.INCLUDE);
        assertEquals("{\"readAttributes\":\"name,pop\"}", builder.buildKey(v));
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
        assertEquals("{\"clipVectorFilter\":\"" + TRIANGLE_WKT + "\"}", builder.buildKey(v));
    }

    @Test
    public void testVectorIntersect() {
        VectorAccessLimits v = new VectorAccessLimits(CatalogMode.HIDE, null, Filter.INCLUDE, null, Filter.INCLUDE);
        v.setIntersectVectorFilter(GF.createPoint(new Coordinate(5, 5)));
        assertEquals("{\"intersectVectorFilter\":\"POINT (5 5)\"}", builder.buildKey(v));
    }

    @Test
    public void testCoverageRasterFilter() {
        CoverageAccessLimits c = new CoverageAccessLimits(CatalogMode.HIDE, Filter.INCLUDE, triangle(), null);
        assertEquals("{\"rasterFilter\":\"" + TRIANGLE_WKT + "\"}", builder.buildKey(c));
    }

    @Test
    public void testCoverageIgnorableParam() {
        Parameter<Boolean> mt = new Parameter<>(ImageMosaicFormat.ALLOW_MULTITHREADING, true);
        CoverageAccessLimits c =
                new CoverageAccessLimits(CatalogMode.HIDE, Filter.INCLUDE, null, new GeneralParameterValue[] {mt});
        assertNull(builder.buildKey(c));
    }

    @Test
    public void testCoverageParam() {
        DefaultParameterDescriptor<String> desc = new DefaultParameterDescriptor<>("BANDS", String.class, null, null);
        Parameter<String> bands = new Parameter<>(desc, "1,2,3");
        CoverageAccessLimits c =
                new CoverageAccessLimits(CatalogMode.HIDE, Filter.INCLUDE, null, new GeneralParameterValue[] {bands});
        assertEquals("{\"BANDS\":\"1,2,3\"}", builder.buildKey(c));
    }

    @Test
    public void testCoverageUnknownParamThrows() {
        DefaultParameterDescriptor<double[]> desc =
                new DefaultParameterDescriptor<>("MyArray", double[].class, null, null);
        Parameter<double[]> bad = new Parameter<>(desc, new double[] {1.0, 2.0});
        CoverageAccessLimits c =
                new CoverageAccessLimits(CatalogMode.HIDE, Filter.INCLUDE, null, new GeneralParameterValue[] {bad});
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> builder.buildKey(c));
        assertThat(ex.getMessage(), containsString("MyArray"));
        assertThat(ex.getMessage(), containsString(IgnorableParameterRegistry.SYSTEM_PROPERTY));
    }

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
        assertEquals(
                "[{\"layer\":\"ws:a\",\"readFilter\":\"population > 0\"},{\"layer\":\"ws:b\"}]",
                builder.buildLayerGroupKey(List.of("ws:a", "ws:b"), List.of(restricted, open)));
    }

    @Test
    public void testLayerGroupSizeMismatch() {
        assertThrows(IllegalArgumentException.class, () -> builder.buildLayerGroupKey(List.of("a"), List.of()));
    }

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
        assertEquals("{\"TAG\":\"HELLO\"}", b2.buildKey(c));
    }

    @Test
    public void testDuplicateCustomSerializerFails() {
        ParameterValueKeySerializer<String> s1 = new TypedKeySerializer<>(String.class);
        ParameterValueKeySerializer<String> s2 = new TypedKeySerializer<>(String.class);
        assertThrows(IllegalStateException.class, () -> new AccessLimitsKeyBuilder(List.of(s1, s2), ignorable));
    }

    @Test
    public void testFilterNormalization() throws Exception {
        DataAccessLimits a = new DataAccessLimits(CatalogMode.HIDE, ECQL.toFilter("13 = population"));
        DataAccessLimits b = new DataAccessLimits(CatalogMode.HIDE, ECQL.toFilter("population = 13"));
        assertEquals(builder.buildKey(a), builder.buildKey(b));
    }

    @Test
    public void testTimeParam() {
        Date t1 = new Date(1000L);
        Date t2 = new Date(2000L);
        DefaultParameterDescriptor<List> desc = new DefaultParameterDescriptor<>("TIME", List.class, null, null);
        Parameter<List> time = new Parameter<>(desc, List.of(t1, t2));
        CoverageAccessLimits c =
                new CoverageAccessLimits(CatalogMode.HIDE, Filter.INCLUDE, null, new GeneralParameterValue[] {time});
        assertEquals("{\"TIME\":\"1970-01-01T00:00:01.000Z,1970-01-01T00:00:02.000Z\"}", builder.buildKey(c));
    }

    @Test
    public void testTimeSorted() {
        Date t1 = new Date(1000L);
        Date t2 = new Date(2000L);
        DefaultParameterDescriptor<List> desc = new DefaultParameterDescriptor<>("TIME", List.class, null, null);
        Parameter<List> fwd = new Parameter<>(desc, List.of(t1, t2));
        Parameter<List> rev = new Parameter<>(desc, List.of(t2, t1));
        CoverageAccessLimits ca =
                new CoverageAccessLimits(CatalogMode.HIDE, Filter.INCLUDE, null, new GeneralParameterValue[] {fwd});
        CoverageAccessLimits cb =
                new CoverageAccessLimits(CatalogMode.HIDE, Filter.INCLUDE, null, new GeneralParameterValue[] {rev});
        assertEquals(builder.buildKey(ca), builder.buildKey(cb));
    }

    @Test
    public void testDateRange() {
        DateRange range = new DateRange(new Date(1000L), new Date(2000L));
        DefaultParameterDescriptor<List> desc = new DefaultParameterDescriptor<>("TIME", List.class, null, null);
        Parameter<List> time = new Parameter<>(desc, List.of(range));
        CoverageAccessLimits c =
                new CoverageAccessLimits(CatalogMode.HIDE, Filter.INCLUDE, null, new GeneralParameterValue[] {time});
        assertEquals("{\"TIME\":\"1970-01-01T00:00:01.000Z/1970-01-01T00:00:02.000Z\"}", builder.buildKey(c));
    }

    @Test
    public void testNumberRange() {
        NumberRange<Double> range = new NumberRange<>(Double.class, 100.0, 200.0);
        DefaultParameterDescriptor<List> desc = new DefaultParameterDescriptor<>("ELEVATION", List.class, null, null);
        Parameter<List> elev = new Parameter<>(desc, List.of(range));
        CoverageAccessLimits c =
                new CoverageAccessLimits(CatalogMode.HIDE, Filter.INCLUDE, null, new GeneralParameterValue[] {elev});
        assertEquals("{\"ELEVATION\":\"100.0/200.0\"}", builder.buildKey(c));
    }

    @Test
    public void testCustomDimension() {
        DefaultParameterDescriptor<List> desc = new DefaultParameterDescriptor<>("MY_DIM", List.class, null, null);
        Parameter<List> dim = new Parameter<>(desc, List.of("A", "B", "C"));
        CoverageAccessLimits c =
                new CoverageAccessLimits(CatalogMode.HIDE, Filter.INCLUDE, null, new GeneralParameterValue[] {dim});
        assertEquals("{\"MY_DIM\":\"A,B,C\"}", builder.buildKey(c));
    }
}
