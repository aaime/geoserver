/* (c) 2020 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog.impl;

import static org.geoserver.catalog.CatalogCloneVisitor.DEFAULT_COPY_PREFIX;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.net.URL;
import java.util.Date;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogBuilder;
import org.geoserver.catalog.CatalogCloneVisitor;
import org.geoserver.catalog.CatalogInfo;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.ResourceInfo;
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
import org.geoserver.test.http.MockHttpClient;
import org.geoserver.test.http.MockHttpResponse;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.styling.PointSymbolizer;
import org.geotools.styling.Style;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Test;
import org.opengis.filter.FilterFactory2;

public class CatalogCloneVisitorTest extends CascadeVisitorAbstractTest {

    static final String[] PREFIXES = new String[] {"CopyOf", "YetAnother"};
    static final FilterFactory2 FF = CommonFactoryFinder.getFilterFactory2();
    private static final Class[] CATALOG_INFO_TYPES = {
        StyleInfo.class,
        LayerGroupInfo.class,
        LayerInfo.class,
        ResourceInfo.class,
        StoreInfo.class,
        NamespaceInfo.class,
        WorkspaceInfo.class
    };
    private static final String WMS_STORE_NAME = "wmsCascade";
    private static final String WMS_LAYER_NAME = "world4326";
    private static final String WMTS_STORE_NAME = "test-wmts-store";
    private static final String WMTS_LAYER_NAME = "AMSR2_Wind_Speed_Night";
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
    }

    @Override
    protected void onTearDown(SystemTestData testData) throws Exception {
        super.onTearDown(testData);
    }

    @Before
    public void resetChanges() throws IOException {
        this.catalog = getCatalog();

        // assumes whatever starts with the known prefixes needs to be removed
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
                        catalog.remove(info);
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
        DataStoreInfo store = catalog.getStoreByName(MockData.CITE_PREFIX, DataStoreInfo.class);
        CatalogCloneVisitor visitor = new CatalogCloneVisitor(catalog);
        store.accept(visitor);

        assertDataStoreCopy(store, "CopyOf" + MockData.CITE_PREFIX);
        assertNull(catalog.getLayerByName("CopyOfLakes"));
        assertNull(catalog.getFeatureTypeByName("CopyOfLakes"));
    }

    @Test
    public void testCopyDataStoreRecursive() throws Exception {
        DataStoreInfo store = catalog.getStoreByName(MockData.CITE_PREFIX, DataStoreInfo.class);
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
}
