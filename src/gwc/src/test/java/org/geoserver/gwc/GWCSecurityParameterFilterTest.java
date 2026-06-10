/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.gwc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.xml.namespace.QName;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogBuilder;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.data.test.CiteTestData;
import org.geoserver.data.test.MockData;
import org.geoserver.data.test.SystemTestData;
import org.geoserver.data.test.SystemTestData.LayerProperty;
import org.geoserver.gwc.security.CustomParam;
import org.geoserver.gwc.security.CustomParamSerializer;
import org.geoserver.gwc.security.SecurityKeyHolder;
import org.geoserver.security.CatalogMode;
import org.geoserver.security.CoverageAccessLimits;
import org.geoserver.security.TestResourceAccessManager;
import org.geoserver.security.VectorAccessLimits;
import org.geoserver.test.GeoServerSystemTestSupport;
import org.geotools.api.filter.Filter;
import org.geotools.api.filter.FilterFactory;
import org.geotools.api.filter.expression.PropertyName;
import org.geotools.api.parameter.GeneralParameterValue;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.filter.text.ecql.ECQL;
import org.geotools.image.ImageWorker;
import org.geotools.parameter.DefaultParameterDescriptor;
import org.geotools.parameter.Parameter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Integration tests for GWC security parameter filter. Verifies tile cache segmentation per user access limits across
 * vector, raster, and layer group scenarios, covering all security limit types.
 */
public class GWCSecurityParameterFilterTest extends GeoServerSystemTestSupport {

    static final QName MOSAIC = new QName(MockData.SF_URI, "mosaic", MockData.SF_PREFIX);
    static final String GROUP = "secTestGroup";

    static final GeometryFactory GF = new GeometryFactory();
    static final FilterFactory FF = CommonFactoryFinder.getFilterFactory();

    // clip polygons for vector clip tests — within BASIC_POLYGONS/LAKES feature extent
    static final MultiPolygon CLIP_A = bbox(-2, 0, 3, 5);
    static final MultiPolygon CLIP_B = bbox(0, 2, 4, 6);

    static final MultiPolygon RASTER_CLIP_A = bbox(10, 20, 50, 60);
    static final MultiPolygon RASTER_CLIP_B = bbox(100, 30, 150, 70);

    @Override
    protected void setUpSpring(List<String> springContextLocations) {
        super.setUpSpring(springContextLocations);
        springContextLocations.add("classpath:/org/geoserver/wms/ResourceAccessManagerContext.xml");
        springContextLocations.add("classpath:/org/geoserver/gwc/security/CustomParamSerializerContext.xml");
    }

    @Override
    protected void onSetUp(SystemTestData testData) throws Exception {
        super.onSetUp(testData);
        GWC.get().getConfig().setDirectWMSIntegrationEnabled(true);
        addRasterLayer(testData);
        addLayerGroup();
    }

    private void addRasterLayer(SystemTestData testData) throws Exception {
        testData.addStyle("raster", "raster.sld", SystemTestData.class, getCatalog());
        Map<LayerProperty, Object> props = new HashMap<>();
        props.put(LayerProperty.STYLE, "raster");
        testData.addRasterLayer(MOSAIC, "raster-filter-test.zip", null, props, SystemTestData.class, getCatalog());
        CoverageInfo ci = getCatalog().getCoverageByName("sf:mosaic");
        ci.setNativeBoundingBox(CiteTestData.DEFAULT_LATLON_ENVELOPE);
        getCatalog().save(ci);
    }

    private void addLayerGroup() throws Exception {
        Catalog catalog = getCatalog();
        LayerGroupInfo group = catalog.getFactory().createLayerGroup();
        group.setName(GROUP);
        LayerInfo lakes = catalog.getLayerByName(getLayerId(MockData.LAKES));
        LayerInfo forests = catalog.getLayerByName(getLayerId(MockData.FORESTS));
        group.getLayers().add(lakes);
        group.getLayers().add(forests);
        group.getStyles().add(null);
        group.getStyles().add(null);
        new CatalogBuilder(catalog).calculateLayerGroupBounds(group);
        catalog.add(group);
    }

    @Before
    public void resetState() throws Exception {
        GWC.get().getConfig().setSecurityEnabled(false);
        GWC gwc = GWC.get();
        gwc.truncate(getLayerId(MockData.BASIC_POLYGONS));
        gwc.truncate("sf:mosaic");
        gwc.truncate(GROUP);
        getRAM().clearLimits();
        SecurityContextHolder.clearContext();
        SecurityKeyHolder.clear();
    }

    @After
    public void cleanUp() {
        SecurityContextHolder.clearContext();
        SecurityKeyHolder.clear();
    }

    private TestResourceAccessManager getRAM() {
        return (TestResourceAccessManager) applicationContext.getBean("testResourceAccessManager");
    }

    /** Tile request at EPSG:4326:0 — world-scale tile, suitable for all test layers. */
    private void assertTileResult(QName layer, String expected) throws Exception {
        assertTileResult(getLayerId(layer), 0, expected);
    }

    private void assertRasterTileResult(String expected) throws Exception {
        assertTileResult("sf:mosaic", 0, expected);
    }

    private void assertTileResult(String layerId, int col, String expected) throws Exception {
        String path = "gwc/service/wmts?request=GetTile&layer=" + layerId
                + "&format=image/png&tilematrixset=EPSG:4326&tilematrix=EPSG:4326:0"
                + "&tilerow=0&tilecol=" + col;
        MockHttpServletResponse response = getAsServletResponse(path);
        assertEquals("HTTP status for " + layerId, 200, response.getStatus());
        assertEquals("image/png", response.getContentType());
        assertThat(
                "Cache result for " + layerId,
                response.getHeader("geowebcache-cache-result"),
                equalToIgnoringCase(expected));
    }

    private static MultiPolygon bbox(double minX, double minY, double maxX, double maxY) {
        Polygon p = GF.createPolygon(new Coordinate[] {
            new Coordinate(minX, minY),
            new Coordinate(minX, maxY),
            new Coordinate(maxX, maxY),
            new Coordinate(maxX, minY),
            new Coordinate(minX, minY)
        });
        return GF.createMultiPolygon(new Polygon[] {p});
    }

    private static VectorAccessLimits vectorFilter(String ecql) throws Exception {
        return new VectorAccessLimits(CatalogMode.HIDE, null, ECQL.toFilter(ecql), null, Filter.INCLUDE);
    }

    private static VectorAccessLimits vectorClip(MultiPolygon clip) {
        return new VectorAccessLimits(CatalogMode.HIDE, null, Filter.INCLUDE, null, Filter.INCLUDE, clip);
    }

    private static VectorAccessLimits vectorIntersect(MultiPolygon intersect) {
        VectorAccessLimits limits =
                new VectorAccessLimits(CatalogMode.HIDE, null, Filter.INCLUDE, null, Filter.INCLUDE);
        limits.setIntersectVectorFilter(intersect);
        return limits;
    }

    private static GeneralParameterValue[] rasterParam(String value) {
        DefaultParameterDescriptor<String> desc =
                new DefaultParameterDescriptor<>("SECURITY_PARAM", String.class, null, null);
        return new GeneralParameterValue[] {new Parameter<>(desc, value)};
    }

    private static GeneralParameterValue[] customParam(String value) {
        return new GeneralParameterValue[] {new Parameter<>(CustomParamSerializer.DESCRIPTOR, new CustomParam(value))};
    }

    @Test
    public void testSecurityDisabledSharesCache() throws Exception {
        // all users map to the same parametersId when security is off
        login("user_a", "test");
        assertTileResult(MockData.BASIC_POLYGONS, "MISS");

        login("user_b", "test");
        assertTileResult(MockData.BASIC_POLYGONS, "HIT");
    }

    @Test
    public void testVectorUnrestrictedSharesCache() throws Exception {
        GWC.get().getConfig().setSecurityEnabled(true);
        login("user_a", "test");
        assertTileResult(MockData.BASIC_POLYGONS, "MISS");

        login("user_b", "test");
        assertTileResult(MockData.BASIC_POLYGONS, "HIT");
    }

    @Test
    public void testVectorReadFilterSeparatesCache() throws Exception {
        GWC.get().getConfig().setSecurityEnabled(true);
        LayerInfo layer = getCatalog().getLayerByName(getLayerId(MockData.BASIC_POLYGONS));
        getRAM().putLimits("user_b", layer.getResource(), vectorFilter("FID = 'BasicPolygons.1107531493630'"));

        login("user_a", "test");
        assertTileResult(MockData.BASIC_POLYGONS, "MISS");

        login("user_b", "test");
        assertTileResult(MockData.BASIC_POLYGONS, "MISS"); // different key → own cache

        login("user_b", "test");
        assertTileResult(MockData.BASIC_POLYGONS, "HIT");
    }

    @Test
    public void testVectorDifferentReadFiltersSeparateCaches() throws Exception {
        GWC.get().getConfig().setSecurityEnabled(true);
        LayerInfo layer = getCatalog().getLayerByName(getLayerId(MockData.BASIC_POLYGONS));
        getRAM().putLimits("user_a", layer.getResource(), vectorFilter("FID = 'BasicPolygons.1107531493630'"));
        getRAM().putLimits("user_b", layer.getResource(), vectorFilter("FID = 'BasicPolygons.1107531493643'"));

        login("user_a", "test");
        assertTileResult(MockData.BASIC_POLYGONS, "MISS");

        login("user_b", "test");
        assertTileResult(MockData.BASIC_POLYGONS, "MISS"); // different filter → own cache

        login("user_a", "test");
        assertTileResult(MockData.BASIC_POLYGONS, "HIT");
    }

    @Test
    public void testVectorSameReadFilterSharesCache() throws Exception {
        GWC.get().getConfig().setSecurityEnabled(true);
        LayerInfo layer = getCatalog().getLayerByName(getLayerId(MockData.BASIC_POLYGONS));
        VectorAccessLimits shared = vectorFilter("FID = 'BasicPolygons.1107531493630'");
        getRAM().putLimits("user_a", layer.getResource(), shared);
        getRAM().putLimits("user_b", layer.getResource(), shared);

        login("user_a", "test");
        assertTileResult(MockData.BASIC_POLYGONS, "MISS");

        login("user_b", "test");
        assertTileResult(MockData.BASIC_POLYGONS, "HIT");
    }

    @Test
    public void testVectorReadAttributesSeparatesCache() throws Exception {
        GWC.get().getConfig().setSecurityEnabled(true);
        LayerInfo layer = getCatalog().getLayerByName(getLayerId(MockData.BASIC_POLYGONS));
        // user_a: unrestricted; user_b: restricted to geometry only (no ID)
        // the_geom must be included or WMS renderer cannot render the layer
        getRAM().putLimits(
                        "user_b",
                        layer.getResource(),
                        new VectorAccessLimits(
                                CatalogMode.HIDE,
                                List.of(FF.property("the_geom")),
                                Filter.INCLUDE,
                                null,
                                Filter.INCLUDE));

        login("user_a", "test");
        assertTileResult(MockData.BASIC_POLYGONS, "MISS");

        login("user_b", "test");
        assertTileResult(MockData.BASIC_POLYGONS, "MISS"); // attribute restriction → different key

        login("user_b", "test");
        assertTileResult(MockData.BASIC_POLYGONS, "HIT");
    }

    @Test
    public void testVectorSameReadAttributesShareCache() throws Exception {
        GWC.get().getConfig().setSecurityEnabled(true);
        LayerInfo layer = getCatalog().getLayerByName(getLayerId(MockData.BASIC_POLYGONS));
        List<PropertyName> attrs = List.of(FF.property("the_geom"));
        getRAM().putLimits(
                        "user_a",
                        layer.getResource(),
                        new VectorAccessLimits(CatalogMode.HIDE, attrs, Filter.INCLUDE, null, Filter.INCLUDE));
        getRAM().putLimits(
                        "user_b",
                        layer.getResource(),
                        new VectorAccessLimits(CatalogMode.HIDE, attrs, Filter.INCLUDE, null, Filter.INCLUDE));

        login("user_a", "test");
        assertTileResult(MockData.BASIC_POLYGONS, "MISS");

        login("user_b", "test");
        assertTileResult(MockData.BASIC_POLYGONS, "HIT");
    }

    @Test
    public void testVectorClipGeometrySeparatesCache() throws Exception {
        GWC.get().getConfig().setSecurityEnabled(true);
        LayerInfo layer = getCatalog().getLayerByName(getLayerId(MockData.BASIC_POLYGONS));
        getRAM().putLimits("user_a", layer.getResource(), vectorClip(CLIP_A));
        getRAM().putLimits("user_b", layer.getResource(), vectorClip(CLIP_B));

        login("user_a", "test");
        assertTileResult(MockData.BASIC_POLYGONS, "MISS");

        login("user_b", "test");
        assertTileResult(MockData.BASIC_POLYGONS, "MISS"); // different clip → different key

        login("user_a", "test");
        assertTileResult(MockData.BASIC_POLYGONS, "HIT");
    }

    @Test
    public void testVectorSameClipGeometrySharesCache() throws Exception {
        GWC.get().getConfig().setSecurityEnabled(true);
        LayerInfo layer = getCatalog().getLayerByName(getLayerId(MockData.BASIC_POLYGONS));
        getRAM().putLimits("user_a", layer.getResource(), vectorClip(CLIP_A));
        getRAM().putLimits("user_b", layer.getResource(), vectorClip(CLIP_A));

        login("user_a", "test");
        assertTileResult(MockData.BASIC_POLYGONS, "MISS");

        login("user_b", "test");
        assertTileResult(MockData.BASIC_POLYGONS, "HIT");
    }

    @Test
    public void testRasterUnrestrictedSharesCache() throws Exception {
        GWC.get().getConfig().setSecurityEnabled(true);

        login("user_a", "test");
        assertRasterTileResult("MISS");

        login("user_b", "test");
        assertRasterTileResult("HIT");
    }

    @Test
    public void testRasterFilterSeparatesCache() throws Exception {
        GWC.get().getConfig().setSecurityEnabled(true);
        CoverageInfo coverage = getCatalog().getCoverageByName("sf:mosaic");
        // raster clip must fully contain the requested tile (0,-90)-(180,90) to pass security
        getRAM().putLimits(
                        "user_b",
                        coverage,
                        new CoverageAccessLimits(CatalogMode.HIDE, Filter.INCLUDE, RASTER_CLIP_A, null));

        login("user_a", "test");
        assertRasterTileResult("MISS");

        login("user_b", "test");
        assertRasterTileResult("MISS"); // raster clip key → own cache

        login("user_b", "test");
        assertRasterTileResult("HIT");
    }

    @Test
    public void testRasterDifferentFiltersSeparateCaches() throws Exception {
        GWC.get().getConfig().setSecurityEnabled(true);
        CoverageInfo coverage = getCatalog().getCoverageByName("sf:mosaic");
        getRAM().putLimits(
                        "user_a",
                        coverage,
                        new CoverageAccessLimits(CatalogMode.HIDE, Filter.INCLUDE, RASTER_CLIP_A, null));
        getRAM().putLimits(
                        "user_b",
                        coverage,
                        new CoverageAccessLimits(CatalogMode.HIDE, Filter.INCLUDE, RASTER_CLIP_B, null));

        login("user_a", "test");
        assertRasterTileResult("MISS");

        login("user_b", "test");
        assertRasterTileResult("MISS"); // different clip → own cache

        login("user_a", "test");
        assertRasterTileResult("HIT");
    }

    @Test
    public void testRasterSameFilterSharesCache() throws Exception {
        GWC.get().getConfig().setSecurityEnabled(true);
        CoverageInfo coverage = getCatalog().getCoverageByName("sf:mosaic");
        CoverageAccessLimits shared = new CoverageAccessLimits(CatalogMode.HIDE, Filter.INCLUDE, RASTER_CLIP_A, null);
        getRAM().putLimits("user_a", coverage, shared);
        getRAM().putLimits("user_b", coverage, shared);

        login("user_a", "test");
        assertRasterTileResult("MISS");

        login("user_b", "test");
        assertRasterTileResult("HIT");
    }

    @Test
    public void testGroupUnrestrictedSharesCache() throws Exception {
        GWC.get().getConfig().setSecurityEnabled(true);

        login("user_a", "test");
        assertTileResult(GROUP, 0, "MISS");

        login("user_b", "test");
        assertTileResult(GROUP, 0, "HIT");
    }

    @Test
    public void testGroupPartialRestrictionSeparatesCache() throws Exception {
        GWC.get().getConfig().setSecurityEnabled(true);
        LayerInfo lakes = getCatalog().getLayerByName(getLayerId(MockData.LAKES));
        // user_b restricted on one group layer; group key changes even if user sees other layers
        getRAM().putLimits("user_b", lakes.getResource(), vectorFilter("NAME = 'Blue Lake'"));

        login("user_a", "test");
        assertTileResult(GROUP, 0, "MISS");

        login("user_b", "test");
        assertTileResult(GROUP, 0, "MISS"); // restriction on group member → different key

        login("user_b", "test");
        assertTileResult(GROUP, 0, "HIT");
    }

    @Test
    public void testGroupDifferentRestrictionsSeparateCaches() throws Exception {
        GWC.get().getConfig().setSecurityEnabled(true);
        LayerInfo lakes = getCatalog().getLayerByName(getLayerId(MockData.LAKES));
        getRAM().putLimits("user_a", lakes.getResource(), vectorFilter("NAME = 'Blue Lake'"));
        getRAM().putLimits("user_b", lakes.getResource(), vectorFilter("NAME = 'Green Lake'"));

        login("user_a", "test");
        assertTileResult(GROUP, 0, "MISS");

        login("user_b", "test");
        assertTileResult(GROUP, 0, "MISS"); // different restriction → own cache

        login("user_a", "test");
        assertTileResult(GROUP, 0, "HIT");
    }

    @Test
    public void testGroupSameRestrictionsShareCache() throws Exception {
        GWC.get().getConfig().setSecurityEnabled(true);
        LayerInfo lakes = getCatalog().getLayerByName(getLayerId(MockData.LAKES));
        VectorAccessLimits shared = vectorFilter("NAME = 'Blue Lake'");
        getRAM().putLimits("user_a", lakes.getResource(), shared);
        getRAM().putLimits("user_b", lakes.getResource(), shared);

        login("user_a", "test");
        assertTileResult(GROUP, 0, "MISS");

        login("user_b", "test");
        assertTileResult(GROUP, 0, "HIT");
    }

    @Test
    public void testVectorIntersectFilterSeparatesCache() throws Exception {
        GWC.get().getConfig().setSecurityEnabled(true);
        LayerInfo layer = getCatalog().getLayerByName(getLayerId(MockData.BASIC_POLYGONS));
        getRAM().putLimits("user_a", layer.getResource(), vectorIntersect(CLIP_A));
        getRAM().putLimits("user_b", layer.getResource(), vectorIntersect(CLIP_B));

        login("user_a", "test");
        assertTileResult(MockData.BASIC_POLYGONS, "MISS");

        login("user_b", "test");
        assertTileResult(MockData.BASIC_POLYGONS, "MISS"); // different intersect → different key

        login("user_a", "test");
        assertTileResult(MockData.BASIC_POLYGONS, "HIT");
    }

    @Test
    public void testVectorSameIntersectFilterSharesCache() throws Exception {
        GWC.get().getConfig().setSecurityEnabled(true);
        LayerInfo layer = getCatalog().getLayerByName(getLayerId(MockData.BASIC_POLYGONS));
        getRAM().putLimits("user_a", layer.getResource(), vectorIntersect(CLIP_A));
        getRAM().putLimits("user_b", layer.getResource(), vectorIntersect(CLIP_A));

        login("user_a", "test");
        assertTileResult(MockData.BASIC_POLYGONS, "MISS");

        login("user_b", "test");
        assertTileResult(MockData.BASIC_POLYGONS, "HIT");
    }

    @Test
    public void testSecurityTagsSeparateCache() throws Exception {
        GWC.get().getConfig().setSecurityEnabled(true);
        LayerInfo layer = getCatalog().getLayerByName(getLayerId(MockData.BASIC_POLYGONS));
        VectorAccessLimits noTags = vectorFilter("FID = 'BasicPolygons.1107531493630'");
        VectorAccessLimits withTags = vectorFilter("FID = 'BasicPolygons.1107531493630'");
        withTags.setSecurityTags(Set.of("tenant-a"));
        getRAM().putLimits("user_a", layer.getResource(), noTags);
        getRAM().putLimits("user_b", layer.getResource(), withTags);

        login("user_a", "test");
        assertTileResult(MockData.BASIC_POLYGONS, "MISS");

        // same ACCESS_LIMITS_KEY but different SECURITY_TAGS_KEY → own cache
        login("user_b", "test");
        assertTileResult(MockData.BASIC_POLYGONS, "MISS");

        login("user_b", "test");
        assertTileResult(MockData.BASIC_POLYGONS, "HIT");
    }

    @Test
    public void testSecurityTagsShareCacheWhenIdentical() throws Exception {
        GWC.get().getConfig().setSecurityEnabled(true);
        LayerInfo layer = getCatalog().getLayerByName(getLayerId(MockData.BASIC_POLYGONS));
        VectorAccessLimits limitsA = vectorFilter("FID = 'BasicPolygons.1107531493630'");
        limitsA.setSecurityTags(Set.of("tenant-a"));
        VectorAccessLimits limitsB = vectorFilter("FID = 'BasicPolygons.1107531493630'");
        limitsB.setSecurityTags(Set.of("tenant-a"));
        getRAM().putLimits("user_a", layer.getResource(), limitsA);
        getRAM().putLimits("user_b", layer.getResource(), limitsB);

        login("user_a", "test");
        assertTileResult(MockData.BASIC_POLYGONS, "MISS");

        login("user_b", "test");
        assertTileResult(MockData.BASIC_POLYGONS, "HIT");
    }

    @Test
    public void testRasterParamsSeparatesCache() throws Exception {
        GWC.get().getConfig().setSecurityEnabled(true);
        CoverageInfo coverage = getCatalog().getCoverageByName("sf:mosaic");
        getRAM().putLimits(
                        "user_a",
                        coverage,
                        new CoverageAccessLimits(CatalogMode.HIDE, Filter.INCLUDE, null, rasterParam("zone-1")));
        getRAM().putLimits(
                        "user_b",
                        coverage,
                        new CoverageAccessLimits(CatalogMode.HIDE, Filter.INCLUDE, null, rasterParam("zone-2")));

        login("user_a", "test");
        assertRasterTileResult("MISS");

        login("user_b", "test");
        assertRasterTileResult("MISS"); // different param value → own cache

        login("user_a", "test");
        assertRasterTileResult("HIT");
    }

    @Test
    public void testRasterSameParamsSharesCache() throws Exception {
        GWC.get().getConfig().setSecurityEnabled(true);
        CoverageInfo coverage = getCatalog().getCoverageByName("sf:mosaic");
        getRAM().putLimits(
                        "user_a",
                        coverage,
                        new CoverageAccessLimits(CatalogMode.HIDE, Filter.INCLUDE, null, rasterParam("zone-1")));
        getRAM().putLimits(
                        "user_b",
                        coverage,
                        new CoverageAccessLimits(CatalogMode.HIDE, Filter.INCLUDE, null, rasterParam("zone-1")));

        login("user_a", "test");
        assertRasterTileResult("MISS");

        login("user_b", "test");
        assertRasterTileResult("HIT");
    }

    @Test
    public void testRasterCustomSerializerSeparatesCache() throws Exception {
        // verifies that CustomParamSerializer (registered as a Spring bean in CustomParamSerializerContext.xml)
        // is picked up by AccessLimitsKeyBuilder.afterPropertiesSet() at context startup
        GWC.get().getConfig().setSecurityEnabled(true);
        CoverageInfo coverage = getCatalog().getCoverageByName("sf:mosaic");
        getRAM().putLimits(
                        "user_a",
                        coverage,
                        new CoverageAccessLimits(CatalogMode.HIDE, Filter.INCLUDE, null, customParam("value_a")));
        getRAM().putLimits(
                        "user_b",
                        coverage,
                        new CoverageAccessLimits(CatalogMode.HIDE, Filter.INCLUDE, null, customParam("value_b")));

        login("user_a", "test");
        assertRasterTileResult("MISS");

        login("user_b", "test");
        assertRasterTileResult("MISS"); // different custom param → own cache

        login("user_a", "test");
        assertRasterTileResult("HIT");
    }

    @Test
    public void testRasterClipEnforcedOnTileContent() throws Exception {
        // verifies that the raster clip is actually applied during rendering, not just segregating cache keys.
        // tile col=0 at EPSG:4326:0 covers the western hemisphere (-180,-90,0,90);
        // clip (10,10,20,20) is entirely in the eastern hemisphere → no intersection → null coverage → transparent
        // tile.
        GWC.get().getConfig().setSecurityEnabled(true);
        CoverageInfo coverage = getCatalog().getCoverageByName("sf:mosaic");
        getRAM().putLimits(
                        "user_b",
                        coverage,
                        new CoverageAccessLimits(CatalogMode.HIDE, Filter.INCLUDE, bbox(10, 10, 20, 20), null));

        String path = "gwc/service/wmts?request=GetTile&layer=sf:mosaic"
                + "&format=image/png&tilematrixset=EPSG:4326&tilematrix=EPSG:4326:0&tilerow=0&tilecol=0";

        login("user_a", "test");
        byte[] tileA = getAsServletResponse(path).getContentAsByteArray();

        login("user_b", "test");
        byte[] tileB = getAsServletResponse(path).getContentAsByteArray();

        BufferedImage imgA = ImageIO.read(new ByteArrayInputStream(tileA));
        BufferedImage imgB = ImageIO.read(new ByteArrayInputStream(tileB));
        double[] alphaA = new ImageWorker(imgA).forceComponentColorModel().getMaximums();
        double[] alphaB = new ImageWorker(imgB).forceComponentColorModel().getMaximums();
        assertTrue("unrestricted tile must have visible content", alphaA[alphaA.length - 1] > 0);
        assertEquals("clip outside tile extent must produce transparent tile", 0.0, alphaB[alphaB.length - 1], 0.0);
    }
}
