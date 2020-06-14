/* (c) 2020 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog.impl;

import static org.geoserver.catalog.CatalogCloneVisitor.DEFAULT_COPY_PREFIX;
import static org.geoserver.data.test.MockData.CITE_PREFIX;
import static org.geoserver.data.test.MockData.STREAMS;
import static org.geoserver.platform.resource.Resource.Type.DIRECTORY;
import static org.geoserver.platform.resource.Resource.Type.RESOURCE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Date;
import org.apache.commons.io.FileUtils;
import org.geoserver.catalog.CascadeDeleteVisitor;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogBuilder;
import org.geoserver.catalog.CatalogCloneVisitor;
import org.geoserver.catalog.CatalogFactory;
import org.geoserver.catalog.CatalogInfo;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.ResourcePoolTest;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.catalog.TestHttpClientProvider;
import org.geoserver.catalog.WMSLayerInfo;
import org.geoserver.catalog.WMSStoreInfo;
import org.geoserver.catalog.WMTSLayerInfo;
import org.geoserver.catalog.WMTSStoreInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.util.CloseableIterator;
import org.geoserver.config.GeoServerDataDirectory;
import org.geoserver.data.test.CiteTestData;
import org.geoserver.data.test.MockData;
import org.geoserver.data.test.SystemTestData;
import org.geoserver.platform.resource.Resource;
import org.geoserver.test.http.MockHttpClient;
import org.geoserver.test.http.MockHttpResponse;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.styling.PointSymbolizer;
import org.geotools.styling.Style;
import org.geotools.util.Version;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Test;
import org.opengis.filter.FilterFactory2;

public class CatalogCloneVisitorTest extends CascadeVisitorAbstractTest {

    static final String[] PREFIXES = new String[] {"CopyOf", "YetAnother"};
    static final String WS_GROUP = "citeGroup";
    static final FilterFactory2 FF = CommonFactoryFinder.getFilterFactory2();
    private static final Class[] CATALOG_INFO_TYPES = {
        StyleInfo.class,
        LayerGroupInfo.class,
        LayerInfo.class,
        ResourceInfo.class,
        StoreInfo.class,
        WorkspaceInfo.class
    };
    private static final String WMS_STORE_NAME = "wmsCascade";
    private static final String WMS_LAYER_NAME = "world4326";
    private static final String WMTS_STORE_NAME = "test-wmts-store";
    private static final String WMTS_LAYER_NAME = "AMSR2_Wind_Speed_Night";
    private static final String WS_STYLE2 = "wsStyle2";
    private static final String REFERENCES_STYLES = "references";

    private Catalog catalog;

    @Override
    protected void onSetUp(SystemTestData testData) throws Exception {
        super.onSetUp(testData);

        // have some raster data too
        Catalog catalog = getCatalog();
        testData.addWorkspace(CiteTestData.WCS_PREFIX, CiteTestData.WCS_URI, catalog);
        testData.addDefaultRasterLayer(CiteTestData.TASMANIA_DEM, catalog);
        testData.addDefaultRasterLayer(CiteTestData.TASMANIA_BM, catalog);
        testData.addDefaultRasterLayer(CiteTestData.ROTATED_CAD, catalog);
        testData.addDefaultRasterLayer(CiteTestData.WORLD, catalog);
        testData.addDefaultRasterLayer(SystemTestData.MULTIBAND, catalog);

        // setup a WMS store
        TestHttpClientProvider.startTest();
        String baseURL = TestHttpClientProvider.MOCKSERVER + "/wms11";
        MockHttpClient client = new MockHttpClient();
        URL wmsCapsURL = new URL(baseURL + "?service=WMS&request=GetCapabilities&version=1.1.0");
        client.expectGet(
                wmsCapsURL,
                new MockHttpResponse(getClass().getResource("caps111.xml"), "text/xml"));
        TestHttpClientProvider.bind(client, wmsCapsURL);

        CatalogBuilder cb = new CatalogBuilder(getCatalog());
        WMSStoreInfo wmsStore = cb.buildWMSStore(WMS_STORE_NAME);
        wmsStore.setCapabilitiesURL(wmsCapsURL.toExternalForm());
        catalog.add(wmsStore);
        cb.setStore(wmsStore);
        WMSLayerInfo wmsLayer = cb.buildWMSLayer(WMS_LAYER_NAME);
        catalog.add(wmsLayer);
        catalog.add(cb.buildLayer(wmsLayer));

        // setup a WMTS store
        URL wmtsCapsURL = new URL(baseURL + "?REQUEST=GetCapabilities&VERSION=1.0.0&SERVICE=WMTS");
        client.expectGet(
                wmtsCapsURL,
                new MockHttpResponse(getClass().getResource("nasa.getcapa.xml"), "text/xml"));
        TestHttpClientProvider.bind(client, wmtsCapsURL);
        WMTSStoreInfo wmtsStore = cb.buildWMTSStore(WMTS_STORE_NAME);
        wmtsStore.setCapabilitiesURL(wmtsCapsURL.toExternalForm());
        catalog.add(wmtsStore);
        cb.setStore(wmtsStore);
        WMTSLayerInfo wmtsLayer = cb.buildWMTSLayer(WMTS_LAYER_NAME);
        catalog.add(wmtsLayer);
        catalog.add(cb.buildLayer(wmtsLayer));

        // second workspace specific style
        WorkspaceInfo ws = catalog.getWorkspaceByName(CiteTestData.CITE_PREFIX);
        testData.addStyle(ws, WS_STYLE2, "Streams.sld", SystemTestData.class, catalog);

        // link streams to the style in the same workspace
        testData.addVectorLayer(STREAMS, catalog);
        LayerInfo streams = getCatalog().getLayerByName(getLayerId(MockData.STREAMS));
        StyleInfo streamsCiteStyle = catalog.getStyleByName(CITE_PREFIX, WS_STYLE);
        streams.setDefaultStyle(streamsCiteStyle);
        streams.getStyles().add(catalog.getStyleByName(WS_STYLE2));
        catalog.save(streams);

        // add a style with relative resource references, and resources in sub-directories
        testData.addStyle(
                ws, REFERENCES_STYLES, "se_relativepath.sld", ResourcePoolTest.class, catalog);
        StyleInfo referencesStyle = catalog.getStyleByName(ws, REFERENCES_STYLES);
        referencesStyle.setFormatVersion(new Version("1.1.0"));
        catalog.save(referencesStyle);
        File images = new File(testData.getDataDirectoryRoot(), "workspaces/cite/styles/images");
        assertTrue(images.mkdir());
        File image = new File("./src/test/resources/org/geoserver/catalog/rockFillSymbol.png");
        assertTrue(image.exists());
        FileUtils.copyFileToDirectory(image, images);
        File svg = new File("./src/test/resources/org/geoserver/catalog/square16.svg");
        assertTrue(svg.exists());
        FileUtils.copyFileToDirectory(svg, images);

        // and a workspace specific group, with reference to another group
        // (referencing a global group would not have worked
        CatalogFactory factory = catalog.getFactory();
        LayerGroupInfo wsGroup = factory.createLayerGroup();
        wsGroup.setName(WS_GROUP);
        wsGroup.setWorkspace(ws);
        wsGroup.getLayers().add(catalog.getLayerGroupByName(LAKES_GROUP));
        wsGroup.getLayers().add(catalog.getLayerByName(getLayerId(STREAMS)));
        wsGroup.getStyles().add(null);
        wsGroup.getStyles().add(referencesStyle);
        catalog.add(wsGroup);
    }

    @Override
    protected void onTearDown(SystemTestData testData) throws Exception {
        super.onTearDown(testData);
    }

    @Before
    public void resetChanges() throws IOException {
        this.catalog = getCatalog();

        // assumes whatever starts with the known prefixes needs to be removed
        CascadeDeleteVisitor remover = new CascadeDeleteVisitor(catalog);
        for (String prefix : PREFIXES) {
            for (Class type : CATALOG_INFO_TYPES) {
                try (CloseableIterator<CatalogInfo> iterator =
                        catalog.list(type, FF.like(FF.property("name"), prefix + "*"))) {
                    while (iterator.hasNext()) {
                        CatalogInfo info = iterator.next();
                        // for styles we also need to remove the style file on
                        if (info instanceof StyleInfo) {
                            catalog.getResourcePool().deleteStyle((StyleInfo) info, true);
                        }
                        info.accept(remover);
                    }
                }
            }
        }
    }

    @Test
    public void copyStyle() throws Exception {
        CatalogCloneVisitor visitor = new CatalogCloneVisitor(catalog);
        StyleInfo point = catalog.getStyleByName("point");
        point.accept(visitor);

        assertStyleCopy("CopyOfpoint");
    }

    @Test
    public void copyStyleFileObstacles() throws Exception {
        GeoServerDataDirectory dd = getDataDirectory();
        dd.get("styles/CopyOfPoint.sld").file();
        dd.get("styles/CopyOfPoint2.sld").file();
        CatalogCloneVisitor visitor = new CatalogCloneVisitor(catalog);
        StyleInfo point = catalog.getStyleByName("point");
        point.accept(visitor);

        assertStyleCopy("CopyOfpoint", "CopyOfpoint3");
    }

    @Test
    public void copyStyleNTimes() throws Exception {
        CatalogCloneVisitor visitor = new CatalogCloneVisitor(catalog);
        StyleInfo point = catalog.getStyleByName("point");
        point.accept(visitor);
        assertStyleCopy("CopyOfpoint");
        for (int i = 2; i < 10; i++) {
            point.accept(visitor);
            assertStyleCopy("CopyOfpoint" + i);
        }
    }

    private void assertStyleCopy(String name) throws IOException {
        assertStyleCopy(name, name);
    }

    private void assertStyleCopy(String name, String styleName) throws IOException {
        StyleInfo copyOfpoint = catalog.getStyleByName(name);
        assertNotNull(copyOfpoint);
        assertEquals(name + ".sld", copyOfpoint.getFilename());
        GeoServerDataDirectory dd = getDataDirectory();
        Style style = dd.parsedStyle(copyOfpoint);
        assertThat(
                style.featureTypeStyles().get(0).rules().get(0).symbolizers().get(0),
                CoreMatchers.instanceOf(PointSymbolizer.class));
    }

    @Test
    public void copyLayer() throws Exception {
        CatalogCloneVisitor visitor = new CatalogCloneVisitor(catalog);
        LayerInfo layer = catalog.getLayerByName(getLayerId(MockData.LAKES));
        layer.setDateCreated(new Date());
        layer.setDateModified(new Date());
        // wait some to allow different dates in the copy
        Thread.sleep(200);
        layer.accept(visitor);

        assertLayerCopy(layer);
    }

    @Test
    public void copyLayerNTimes() throws Exception {
        CatalogCloneVisitor visitor = new CatalogCloneVisitor(catalog);
        LayerInfo layer = catalog.getLayerByName(getLayerId(MockData.LAKES));
        layer.accept(visitor);
        assertLayerCopy(layer);
        for (int i = 2; i < 10; i++) {
            layer.accept(visitor);
            assertLayerCopy(layer, "CopyOfLakes" + i);
        }
    }

    private void assertLayerCopy(LayerInfo layer) {
        assertLayerCopy(layer, "CopyOf" + layer.getName());
    }

    private void assertLayerCopy(LayerInfo layer, String expectedName) {
        LayerInfo copy = catalog.getLayerByName(expectedName);
        assertNotNull(copy);
        assertEquals(layer.getDefaultStyle(), copy.getDefaultStyle());
        assertEquals(layer.getStyles(), copy.getStyles());
        if (layer.getDateCreated() != null)
            assertNotEquals(layer.getDateCreated(), copy.getDateCreated());
        if (layer.getDateModified() != null)
            assertNotEquals(layer.getDateModified(), copy.getDateModified());
    }

    @Test
    public void testCopyCoverageStore() throws Exception {
        // non recursive copy
        CoverageStoreInfo store =
                catalog.getStoreByName(
                        MockData.TASMANIA_DEM.getLocalPart(), CoverageStoreInfo.class);
        CatalogCloneVisitor visitor = new CatalogCloneVisitor(catalog);
        store.accept(visitor);

        assertCoverageStoreCopy(store, "CopyOfDEM");
        assertNull(catalog.getLayerByName("CopyOfDEM"));
        assertNull(catalog.getCoverageByName("CopyOfDEM"));
    }

    @Test
    public void testCopyCoverageStoreRecursive() throws Exception {
        CoverageStoreInfo store =
                catalog.getStoreByName(
                        MockData.TASMANIA_DEM.getLocalPart(), CoverageStoreInfo.class);
        CatalogCloneVisitor visitor = new CatalogCloneVisitor(catalog, true);
        store.accept(visitor);

        assertCoverageStoreCopy(store, "CopyOfDEM");
        assertLayerCopy(catalog.getLayerByName("DEM"));
    }

    private void assertCoverageStoreCopy(CoverageStoreInfo store, String expectedName) {
        CoverageStoreInfo copy = catalog.getStoreByName(expectedName, CoverageStoreInfo.class);
        assertNotNull(copy);
        assertEquals(store.getFormat(), copy.getFormat());
        assertEquals(store.getURL(), copy.getURL());
    }

    @Test
    public void testCopyDataStore() throws Exception {
        // non recursive copy
        DataStoreInfo store = catalog.getStoreByName(CITE_PREFIX, DataStoreInfo.class);
        CatalogCloneVisitor visitor = new CatalogCloneVisitor(catalog);
        store.accept(visitor);

        assertDataStoreCopy(store, "CopyOf" + CITE_PREFIX);
        assertNull(catalog.getLayerByName("CopyOfLakes"));
        assertNull(catalog.getFeatureTypeByName("CopyOfLakes"));
    }

    @Test
    public void testCopyDataStoreRecursive() throws Exception {
        DataStoreInfo store = catalog.getStoreByName(CITE_PREFIX, DataStoreInfo.class);
        CatalogCloneVisitor visitor = new CatalogCloneVisitor(catalog, true);
        store.accept(visitor);

        assertDataStoreCopy(store, "CopyOfcite");
        assertLayerCopy(catalog.getLayerByName("Lakes"));
        assertLayerCopy(catalog.getLayerByName("Bridges"));
        assertLayerCopy(catalog.getLayerByName("Forests"));
    }

    private void assertDataStoreCopy(DataStoreInfo store, String expectedName) {
        DataStoreInfo copy = catalog.getStoreByName(expectedName, DataStoreInfo.class);
        assertNotNull(copy);
        assertEquals(store.getConnectionParameters(), copy.getConnectionParameters());
    }

    @Test
    public void testCopyWMSStore() throws Exception {
        // non recursive copy
        WMSStoreInfo store = catalog.getStoreByName(WMS_STORE_NAME, WMSStoreInfo.class);
        CatalogCloneVisitor visitor = new CatalogCloneVisitor(catalog);
        store.accept(visitor);

        assertWMSStoreCopy(store, "CopyOf" + WMS_STORE_NAME);
        assertNull(catalog.getLayerByName("CopyOf" + WMS_LAYER_NAME));
        assertNull(catalog.getResourceByName("CopyOf" + WMS_LAYER_NAME, WMSLayerInfo.class));
    }

    @Test
    public void testCopyWMSStoreRecursive() throws Exception {
        WMSStoreInfo store = catalog.getStoreByName(WMS_STORE_NAME, WMSStoreInfo.class);
        CatalogCloneVisitor visitor = new CatalogCloneVisitor(catalog, true);
        store.accept(visitor);

        assertWMSStoreCopy(store, "CopyOf" + WMS_STORE_NAME);
        assertLayerCopy(catalog.getLayerByName(WMS_LAYER_NAME));
    }

    private void assertWMSStoreCopy(WMSStoreInfo store, String expectedName) {
        WMSStoreInfo copy = catalog.getStoreByName(expectedName, WMSStoreInfo.class);
        assertNotNull(copy);
        assertEquals(store.getCapabilitiesURL(), copy.getCapabilitiesURL());
    }

    @Test
    public void testCopyWMTSStore() throws Exception {
        // non recursive copy
        WMTSStoreInfo store = catalog.getStoreByName(WMTS_STORE_NAME, WMTSStoreInfo.class);
        CatalogCloneVisitor visitor = new CatalogCloneVisitor(catalog);
        store.accept(visitor);

        assertWMTSStoreCopy(store, "CopyOf" + WMTS_STORE_NAME);
        assertNull(catalog.getLayerByName("CopyOf" + WMTS_LAYER_NAME));
        assertNull(catalog.getResourceByName("CopyOf" + WMTS_LAYER_NAME, WMTSLayerInfo.class));
    }

    @Test
    public void testCopyWMTSStoreRecursive() throws Exception {
        WMTSStoreInfo store = catalog.getStoreByName(WMTS_STORE_NAME, WMTSStoreInfo.class);
        CatalogCloneVisitor visitor = new CatalogCloneVisitor(catalog, true);
        store.accept(visitor);

        assertWMTSStoreCopy(store, DEFAULT_COPY_PREFIX + WMTS_STORE_NAME);
        assertLayerCopy(catalog.getLayerByName(WMTS_LAYER_NAME));
    }

    private void assertWMTSStoreCopy(WMTSStoreInfo store, String expectedName) {
        WMTSStoreInfo copy = catalog.getStoreByName(expectedName, WMTSStoreInfo.class);
        assertNotNull(copy);
        assertEquals(store.getCapabilitiesURL(), copy.getCapabilitiesURL());
    }

    @Test
    public void testCopyLayerGroup() throws Exception {
        LayerGroupInfo group = catalog.getLayerGroupByName(LAKES_GROUP);
        CatalogCloneVisitor visitor = new CatalogCloneVisitor(catalog, true);
        group.accept(visitor);

        LayerGroupInfo copy = catalog.getLayerGroupByName(DEFAULT_COPY_PREFIX + LAKES_GROUP);
        assertNotNull(copy);
        // layers were not deep cloned
        assertEquals(group.getLayers(), copy.getLayers());
    }

    @Test
    public void testCopyLayerGroupNested() throws Exception {
        LayerGroupInfo group = catalog.getLayerGroupByName(NEST_GROUP);
        CatalogCloneVisitor visitor = new CatalogCloneVisitor(catalog, true);
        group.accept(visitor);

        LayerGroupInfo copy = catalog.getLayerGroupByName(DEFAULT_COPY_PREFIX + NEST_GROUP);
        assertNotNull(copy);
        // layers were not deep cloned
        assertEquals(group.getLayers(), copy.getLayers());
    }

    // TESTS MISSING: WORKSPACE AND LAYER GROUP! NASTY BIT, THEY NEED TO FOLLOW THE COPIED STYLES
    // AND LAYERS

    @Test
    public void testCopyWorkspace() throws Exception {
        WorkspaceInfo ws = catalog.getWorkspaceByName(CITE_PREFIX);
        NamespaceInfo ns = catalog.getNamespaceByPrefix(CITE_PREFIX);
        CatalogCloneVisitor visitor = new CatalogCloneVisitor(catalog, true);
        ws.accept(visitor);

        WorkspaceInfo wsClone = catalog.getWorkspaceByName("CopyOf" + CITE_PREFIX);
        assertNotNull(wsClone);
        String wsCloneName = wsClone.getName();
        NamespaceInfo nsClone = catalog.getNamespaceByPrefix(wsCloneName);
        assertEquals("http://www.opengis.net/cite2", nsClone.getURI());

        // check the ws specific styles
        StyleInfo wsStyleClone = catalog.getStyleByName(wsClone, WS_STYLE);
        assertNotNull(wsStyleClone);
        assertEquals("wsStyle.sld", wsStyleClone.getFilename());
        // ... the style file has been copied too
        assertNotNull(wsStyleClone.getStyle());

        // check a ws specific style with referenced resources, those should have been copied too
        StyleInfo wsStyleReferencesClone = catalog.getStyleByName(wsClone, REFERENCES_STYLES);
        assertNotNull(wsStyleReferencesClone);
        GeoServerDataDirectory dd = getDataDirectory();
        assertEquals(RESOURCE, dd.getStyles(wsClone, "images/rockFillSymbol.png").getType());
        assertEquals(RESOURCE, dd.getStyles(wsClone, "images/square16.svg").getType());

        // check store, resource and layer copy
        DataStoreInfo store = catalog.getStoreByName(wsCloneName, CITE_PREFIX, DataStoreInfo.class);
        assertNotNull(store);
        FeatureTypeInfo lakesResourceClone =
                catalog.getFeatureTypeByName(wsCloneName + ":" + MockData.LAKES.getLocalPart());
        assertNotNull(lakesResourceClone);
        LayerInfo lakesLayerClone =
                catalog.getLayerByName(wsCloneName + ":" + MockData.LAKES.getLocalPart());
        assertNotNull(lakesLayerClone);
        assertEquals(lakesResourceClone, lakesLayerClone.getResource());

        // the workspace specific style has been associated to the layer
        LayerInfo streamsCloneLayer =
                catalog.getLayerByName(wsCloneName + ":" + STREAMS.getLocalPart());
        assertNotNull(streamsCloneLayer);
        assertEquals(wsStyleClone, streamsCloneLayer.getDefaultStyle());

        // check the ws specific group has been copied over
        LayerGroupInfo group = catalog.getLayerGroupByName(wsCloneName, WS_GROUP);
        assertNotNull(group);
        // ... reference to global group was turned into a local copy because the group
        // was referencing local layers that have been copied over (otherwise validation would fail,
        // a group in CopyOfCite cannot transitively reference layers in Cite
        assertEquals(
                catalog.getLayerGroupByName(wsClone.getName(), LAKES_GROUP),
                group.getLayers().get(0));

        // check the referenced resource has been copied as well to the other workspace
        Resource wsCloneStyles = getDataDirectory().getStyles(wsClone);
        assertEquals(DIRECTORY, wsCloneStyles.getType());
        assertEquals(RESOURCE, wsCloneStyles.get("images/rockFillSymbol.png").getType());
    }

    @Test
    public void testLayerGroupComparator() throws Exception {}
}
