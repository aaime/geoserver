/* (c) 2019 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.api.checkpoint;

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
import org.geotools.gce.imagemosaic.ImageMosaicFormat;
import org.geotools.util.URLs;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.namespace.QName;

public class ChangesetTest extends OGCApiTestSupport {

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
    }

    @Test
    public void testGetSummary() throws Exception {
        // upload single image
        uploadImage("g2.tif");
        
        // TODO: get the summary, get the non summary, from the initial revision
        // TODO: add two images and get the summary and non summary
        // TODO: remove the index if the store gets removed (CatalogListener)
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
                getAsJSONPath( response.getHeader("Location").substring("http://ocalhost:8080/geoserver/".length()),
                        200);
        assertThat(json.read("type"), equalTo("Feature"));
        assertThat(json.read("id"), startsWith("s2."));
        // in case of no date, the unix epoch is used 
        assertThat(json.read("properties.datetime"), equalTo("1970-01-01T00:00:00Z"));
        assertThat(
                json.read("assets[0].href"), endsWith(fileName));
    }

    public byte[] getBytes(String file) throws IOException {
        byte[] payload = null;
        try (InputStream is = new FileInputStream(new File(s2TestData, file))) {
            payload = IOUtils.toByteArray(is);
        }
        return payload;
    }
}
