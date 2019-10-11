/* (c) 2019 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.api.checkpoint;

import static org.geoserver.api.checkpoint.CheckpointIndexProvider.INITIAL_STATE;
import static org.geoserver.ows.util.ResponseUtils.urlEncode;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;

import com.jayway.jsonpath.DocumentContext;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.geoserver.api.OGCApiTestSupport;
import org.geoserver.catalog.CascadeDeleteVisitor;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogBuilder;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.data.test.MockData;
import org.geoserver.data.test.MockTestData;
import org.geoserver.data.test.SystemTestData;
import org.geoserver.gwc.GWC;
import org.geoserver.platform.GeoServerExtensions;
import org.geotools.gce.imagemosaic.ImageMosaicFormat;
import org.geotools.util.URLs;
import org.geowebcache.config.XMLGridSubset;
import org.geowebcache.layer.TileLayer;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.namespace.QName;

public class ChangesetTest extends OGCApiTestSupport {

    static final double EPS = 1e-3;
    public static final String S2_STORE = "s2";
    static final QName S2 = new QName(MockTestData.SF_URI, S2_STORE, MockTestData.SF_PREFIX);
    private File s2TestData;

    @Override
    protected void setUpTestData(SystemTestData testData) throws Exception {
        // no canned test data
    }

    @Before
    public void setupBaseMosaic() throws Exception {
        File s2Directory = getDataDirectory().get(S2_STORE).dir();

        // clean up if the store is there
        Catalog catalog = getCatalog();
        CoverageStoreInfo store = catalog.getStore(S2_STORE, CoverageStoreInfo.class);
        if (store != null) {
            new CascadeDeleteVisitor(catalog).visit(store);
            FileUtils.deleteDirectory(s2Directory);
        }

        // prepare a mosaic with just one tile
        s2Directory.mkdir();
        this.s2TestData = new File("src/test/resources/org/geoserver/api/checkpoint/hetero_s2");
        FileUtils.copyFileToDirectory(new File(s2TestData, "g1.tif"), s2Directory);
        FileUtils.copyFileToDirectory(new File(s2TestData, "indexer.properties"), s2Directory);
        CatalogBuilder cb = new CatalogBuilder(catalog);
        cb.setWorkspace(catalog.getWorkspaceByName(MockData.SF_PREFIX));
        CoverageStoreInfo newStore = cb.buildCoverageStore(S2_STORE);
        newStore.setURL(URLs.fileToUrl(s2Directory).toExternalForm());
        newStore.setType(new ImageMosaicFormat().getName());
        catalog.add(newStore);
        cb.setStore(newStore);
        CoverageInfo ci = cb.buildCoverage();
        catalog.add(ci);
        LayerInfo layer = cb.buildLayer(ci);
        catalog.add(layer);

        // configure tile caching for it
        GWC gwc = GeoServerExtensions.bean(GWC.class);
        TileLayer tileLayer = gwc.getTileLayer(catalog.getLayerByName(getLayerId(S2)));
        XMLGridSubset editableWgs84 = new XMLGridSubset(tileLayer.removeGridSubset("EPSG:4326"));
        editableWgs84.setZoomStart(0);
        editableWgs84.setZoomStop(11);
        tileLayer.addGridSubset(editableWgs84.getGridSubSet(gwc.getGridSetBroker()));
        XMLGridSubset editableWebMercator = new XMLGridSubset(tileLayer.removeGridSubset("EPSG:900913"));
        editableWebMercator.setZoomStart(0);
        editableWebMercator.setZoomStop(11);
        tileLayer.addGridSubset(editableWebMercator.getGridSubSet(gwc.getGridSetBroker()));
        gwc.save(tileLayer);
    }

    @Test
    public void testGetSummarySingle4326() throws Exception {
        // upload single image
        uploadImage("g2.tif");

        DocumentContext doc =
                getAsJSONPath(
                        "ogc/tiles/collections/sf:s2/map/raster/tiles/EPSG:4326?f="
                                + urlEncode(CheckpointTilesService.CHANGESET_MIME),
                        200);

        // the doc contains the requested checkpoint
        assertThat(doc.read("checkpoint"), equalTo(INITIAL_STATE));
        assertThat(doc.read("summaryOfChangedItems[0].priority"), equalTo("medium"));
        // area modified is small, for the zoom levels available
        assertThat(doc.read("summaryOfChangedItems[0].count"), equalTo(18));
        // single modified extent
        assertThat(doc.read("extentOfChangedItems.size()"), equalTo(1));
        assertThat(doc.read("extentOfChangedItems[0].crs"), equalTo("http://www.opengis.net/def/crs/OGC/1.3/CRS84"));
        assertThat(doc.read("extentOfChangedItems[0].bbox[0]"), closeTo(11.683611, EPS));
        assertThat(doc.read("extentOfChangedItems[0].bbox[1]"), closeTo(47.63776, EPS));
        assertThat(doc.read("extentOfChangedItems[0].bbox[2]"), closeTo(11.861294, EPS));
        assertThat(doc.read("extentOfChangedItems[0].bbox[3]"), closeTo(47.754253, EPS));
    }

    @Test
    public void testGetSummarySingle3857() throws Exception {
        // upload single image
        uploadImage("g2.tif");

        DocumentContext doc =
                getAsJSONPath(
                        "ogc/tiles/collections/sf:s2/map/raster/tiles/EPSG:900913?f="
                                + urlEncode(CheckpointTilesService.CHANGESET_MIME),
                        200);

        // the doc contains the requested checkpoint
        assertThat(doc.read("checkpoint"), equalTo(INITIAL_STATE));
        assertThat(doc.read("summaryOfChangedItems[0].priority"), equalTo("medium"));
        // area modified is small, for the zoom levels available
        assertThat(doc.read("summaryOfChangedItems[0].count"), equalTo(16));
        // single modified extent
        assertThat(doc.read("extentOfChangedItems.size()"), equalTo(1));
        assertThat(doc.read("extentOfChangedItems[0].crs"), equalTo("urn:ogc:def:crs:EPSG::900913"));
        assertThat(doc.read("extentOfChangedItems[0].bbox[0]"), closeTo(1300613, 1));
        assertThat(doc.read("extentOfChangedItems[0].bbox[1]"), closeTo(6046801, 1));
        assertThat(doc.read("extentOfChangedItems[0].bbox[2]"), closeTo(1320393, 1));
        assertThat(doc.read("extentOfChangedItems[0].bbox[3]"), closeTo(6066068, 1));
    }

    @Test
    public void testGetSummaryTwo4326() throws Exception {
        // upload two images (toghether they cover the same bbox as g2)
        uploadImage("g3.tif");
        uploadImage("g4.tif");

        DocumentContext doc =
                getAsJSONPath(
                        "ogc/tiles/collections/sf:s2/map/raster/tiles/EPSG:4326?f="
                                + urlEncode(CheckpointTilesService.CHANGESET_MIME),
                        200);

        // the doc contains the requested checkpoint
        assertThat(doc.read("checkpoint"), equalTo(INITIAL_STATE));
        assertThat(doc.read("summaryOfChangedItems[0].priority"), equalTo("medium"));
        // area modified is small, for the zoom levels available
        assertThat(doc.read("summaryOfChangedItems[0].count"), equalTo(18));
        // single modified extent
        assertThat(doc.read("extentOfChangedItems.size()"), equalTo(2));
        assertThat(doc.read("extentOfChangedItems[0].crs"), equalTo("http://www.opengis.net/def/crs/OGC/1.3/CRS84"));
        assertThat(doc.read("extentOfChangedItems[0].bbox[0]"), closeTo(11.683482, EPS));
        assertThat(doc.read("extentOfChangedItems[0].bbox[1]"), closeTo(47.637856, EPS));
        assertThat(doc.read("extentOfChangedItems[0].bbox[2]"), closeTo(11.861166, EPS));
        assertThat(doc.read("extentOfChangedItems[0].bbox[3]"), closeTo(47.754345, EPS));
        assertThat(doc.read("extentOfChangedItems[1].crs"), equalTo("http://www.opengis.net/def/crs/OGC/1.3/CRS84"));
        assertThat(doc.read("extentOfChangedItems[1].bbox[0]"), closeTo(11.683616, EPS));
        assertThat(doc.read("extentOfChangedItems[1].bbox[1]"), closeTo(47.63785, EPS));
        assertThat(doc.read("extentOfChangedItems[1].bbox[2]"), closeTo(11.8612995, EPS));
        assertThat(doc.read("extentOfChangedItems[1].bbox[3]"), closeTo(47.75434, EPS));
    }


    private void uploadImage(String fileName) throws Exception {
        String s2 = getLayerId(S2);
        byte[] payload = getBytes(fileName);
        MockHttpServletResponse response =
                postAsServletResponse(
                        "ogc/images/collections/" + s2 + "/images?filename=" + fileName,
                        payload,
                        "image/tiff");
        assertThat(response.getStatus(), equalTo(201));
        assertThat(
                response.getHeader("Location"),
                startsWith(
                        "http://localhost:8080/geoserver/ogc/images/collections/sf%3As2/images/s2."));

        // check it's really there
        DocumentContext json =
                getAsJSONPath(
                        response.getHeader("Location")
                                .substring("http://localhost:8080/geoserver/".length()),
                        200);
        assertThat(json.read("type"), equalTo("Feature"));
        assertThat(json.read("id"), startsWith("s2."));
        // in case of no date, the unix epoch is used
        assertThat(json.read("properties.datetime"), equalTo("1970-01-01T00:00:00Z"));
        assertThat(json.read("assets[0].href"), endsWith(fileName));
    }

    public byte[] getBytes(String file) throws IOException {
        byte[] payload = null;
        try (InputStream is = new FileInputStream(new File(s2TestData, file))) {
            payload = IOUtils.toByteArray(is);
        }
        return payload;
    }
}
