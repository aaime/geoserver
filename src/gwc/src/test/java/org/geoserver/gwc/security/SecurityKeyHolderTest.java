/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.gwc.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.security.CatalogMode;
import org.geoserver.security.CoverageAccessLimits;
import org.geoserver.security.DataAccessLimits;
import org.geoserver.security.ResourceAccessManager;
import org.geoserver.security.SecureCatalogImpl;
import org.geoserver.security.VectorAccessLimits;
import org.geotools.api.filter.Filter;
import org.geotools.filter.text.ecql.ECQL;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.springframework.context.support.GenericApplicationContext;

public class SecurityKeyHolderTest {

    static final GeometryFactory GF = new GeometryFactory();

    private AccessLimitsKeyBuilder mockKeyBuilder;
    private ResourceAccessManager mockRam;
    private SecureCatalogImpl mockSecureCatalog;
    private LayerInfo mockLayer;
    private GenericApplicationContext ctx;

    @Before
    public void setUp() {
        mockKeyBuilder = mock(AccessLimitsKeyBuilder.class);
        mockRam = mock(ResourceAccessManager.class);
        mockSecureCatalog = mock(SecureCatalogImpl.class);
        when(mockSecureCatalog.getResourceAccessManager()).thenReturn(mockRam);
        mockLayer = mock(LayerInfo.class);
        setContext(mockKeyBuilder);
    }

    @After
    public void tearDown() {
        SecurityKeyHolder.clear();
        new GeoServerExtensions().setApplicationContext(null);
        if (ctx != null) ctx.close();
    }

    private void setContext(AccessLimitsKeyBuilder keyBuilder) {
        if (ctx != null) ctx.close();
        ctx = new GenericApplicationContext();
        ctx.getBeanFactory().registerSingleton("keyBuilder", keyBuilder);
        ctx.getBeanFactory().registerSingleton("secureCatalog", mockSecureCatalog);
        ctx.refresh();
        new GeoServerExtensions().setApplicationContext(ctx);
    }

    private AccessLimitsKeyBuilder realBuilder() {
        return new AccessLimitsKeyBuilder(List.of(), new IgnorableParameterRegistry());
    }

    // --- behavioral tests (mock builder) ---

    @Test
    public void testResolveKeyNoBeans() {
        new GeoServerExtensions().setApplicationContext(null);
        assertNull(SecurityKeyHolder.resolveKey(mockLayer));
    }

    @Test
    public void testResolveKeyUnrestricted() {
        when(mockRam.getAccessLimits(any(), any(LayerInfo.class))).thenReturn(mock(DataAccessLimits.class));
        when(mockKeyBuilder.buildKey(any())).thenReturn(null);
        assertNull(SecurityKeyHolder.resolveKey(mockLayer));
    }

    @Test
    public void testResolveKeyRestricted() {
        when(mockRam.getAccessLimits(any(), any(LayerInfo.class))).thenReturn(mock(DataAccessLimits.class));
        when(mockKeyBuilder.buildKey(any())).thenReturn("user_key");
        assertEquals("user_key", SecurityKeyHolder.resolveKey(mockLayer));
    }

    @Test
    public void testResolveKeyCachesResult() {
        when(mockRam.getAccessLimits(any(), any(LayerInfo.class))).thenReturn(mock(DataAccessLimits.class));
        when(mockKeyBuilder.buildKey(any())).thenReturn("user_key");

        SecurityKeyHolder.resolveKey(mockLayer);
        SecurityKeyHolder.resolveKey(mockLayer);

        // key builder called only once — second call hits thread-local cache
        verify(mockKeyBuilder, times(1)).buildKey(any());
    }

    @Test
    public void testClearAllowsRecompute() {
        when(mockRam.getAccessLimits(any(), any(LayerInfo.class))).thenReturn(mock(DataAccessLimits.class));
        when(mockKeyBuilder.buildKey(any())).thenReturn("user_key");

        SecurityKeyHolder.resolveKey(mockLayer);
        SecurityKeyHolder.clear();
        SecurityKeyHolder.resolveKey(mockLayer);

        verify(mockKeyBuilder, times(2)).buildKey(any());
    }

    @Test
    public void testSingleRamCallForKeyAndTags() {
        // resolveKey + resolveSecurityTags must share one RAM call per request
        when(mockRam.getAccessLimits(any(), any(LayerInfo.class))).thenReturn(mock(DataAccessLimits.class));
        when(mockKeyBuilder.buildKey(any())).thenReturn("user_key");

        SecurityKeyHolder.resolveKey(mockLayer);
        SecurityKeyHolder.resolveSecurityTags(mockLayer);

        verify(mockRam, times(1)).getAccessLimits(any(), any(LayerInfo.class));
    }

    // --- key content tests (real builder) ---
    // expected strings are load-bearing: any change breaks existing tile caches

    @Test
    public void testKeyVectorFilter() throws Exception {
        setContext(realBuilder());
        VectorAccessLimits limits = new VectorAccessLimits(
                CatalogMode.HIDE, null, ECQL.toFilter("population > 1000"), null, Filter.INCLUDE);
        when(mockRam.getAccessLimits(any(), any(LayerInfo.class))).thenReturn(limits);

        assertEquals("{\"readFilter\":\"population > 1000\"}", SecurityKeyHolder.resolveKey(mockLayer));
    }

    @Test
    public void testKeyVectorIncludeFilterIsUnrestricted() {
        setContext(realBuilder());
        VectorAccessLimits limits =
                new VectorAccessLimits(CatalogMode.HIDE, null, Filter.INCLUDE, null, Filter.INCLUDE);
        when(mockRam.getAccessLimits(any(), any(LayerInfo.class))).thenReturn(limits);

        assertThat(SecurityKeyHolder.resolveKey(mockLayer), is(nullValue()));
    }

    @Test
    public void testKeyCoverageRasterClip() {
        setContext(realBuilder());
        Polygon clip = GF.createPolygon(
                new Coordinate[] {new Coordinate(0, 0), new Coordinate(0, 1), new Coordinate(1, 1), new Coordinate(0, 0)
                });
        CoverageAccessLimits limits = new CoverageAccessLimits(
                CatalogMode.HIDE, Filter.INCLUDE, GF.createMultiPolygon(new Polygon[] {clip}), null);
        when(mockRam.getAccessLimits(any(), any(LayerInfo.class))).thenReturn(limits);

        assertEquals(
                "{\"rasterFilter\":\"MULTIPOLYGON (((0 0, 0 1, 1 1, 0 0)))\"}",
                SecurityKeyHolder.resolveKey(mockLayer));
    }

    @Test
    public void testKeyLayerGroupPartialRestriction() throws Exception {
        setContext(realBuilder());
        LayerInfo layerA = mock(LayerInfo.class);
        LayerInfo layerB = mock(LayerInfo.class);
        when(layerA.prefixedName()).thenReturn("ws:a");
        when(layerB.prefixedName()).thenReturn("ws:b");

        LayerGroupInfo group = mock(LayerGroupInfo.class);
        when(group.layers()).thenReturn(List.of(layerA, layerB));

        VectorAccessLimits restricted =
                new VectorAccessLimits(CatalogMode.HIDE, null, ECQL.toFilter("pop > 0"), null, Filter.INCLUDE);
        VectorAccessLimits open = new VectorAccessLimits(CatalogMode.HIDE, null, Filter.INCLUDE, null, Filter.INCLUDE);
        when(mockRam.getAccessLimits(any(), same(layerA))).thenReturn(restricted);
        when(mockRam.getAccessLimits(any(), same(layerB))).thenReturn(open);

        assertEquals(
                "[{\"layer\":\"ws:a\",\"readFilter\":\"pop > 0\"},{\"layer\":\"ws:b\"}]",
                SecurityKeyHolder.resolveKey(group));
    }

    @Test
    public void testKeyLayerGroupAllUnrestricted() {
        setContext(realBuilder());
        LayerInfo layerA = mock(LayerInfo.class);
        LayerInfo layerB = mock(LayerInfo.class);
        when(layerA.prefixedName()).thenReturn("ws:a");
        when(layerB.prefixedName()).thenReturn("ws:b");

        LayerGroupInfo group = mock(LayerGroupInfo.class);
        when(group.layers()).thenReturn(List.of(layerA, layerB));

        when(mockRam.getAccessLimits(any(), any(LayerInfo.class)))
                .thenReturn(new DataAccessLimits(CatalogMode.HIDE, Filter.INCLUDE));

        assertThat(SecurityKeyHolder.resolveKey(group), is(nullValue()));
    }
}
