/* (c) 2018 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wfs3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.jayway.jsonpath.DocumentContext;

import org.apache.commons.io.IOUtils;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.SLDHandler;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.data.test.SystemTestData;
import org.geotools.styling.Mark;
import org.geotools.styling.PointSymbolizer;
import org.geotools.styling.Style;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.w3c.dom.Document;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

public class StyleTest extends WFS3TestSupport {

    @Override
    protected void onSetUp(SystemTestData testData) throws Exception {
        super.onSetUp(testData);
        testData.addStyle("dashed", "dashedline.sld", StyleTest.class, getCatalog());
    }

    @Before
    public void cleanNewStyles() {
        final Catalog catalog = getCatalog();
        Optional.ofNullable(catalog.getStyleByName("simplePoint"))
                .ifPresent(s -> catalog.remove(s));
    }

    @Test
    public void testGetStyles() throws Exception {
        final DocumentContext doc = getAsJSONPath("wfs3/styles", 200);
        // only the dashed line, put as the only non linked style
        assertEquals(Integer.valueOf(1), doc.read("styles.length()", Integer.class));
        assertEquals("dashed", doc.read("styles..id", List.class).get(0));
        assertEquals(
                "http://localhost:8080/geoserver/wfs3/styles/dashed?f=application%2Fvnd.ogc.sld%2Bxml",
                doc.read(
                                "styles..links[?(@.rel=='style' && @.type=='application/vnd.ogc.sld+xml')].href",
                                List.class)
                        .get(0));
    }

    @Test
    public void testGetStyle() throws Exception {
        final MockHttpServletResponse response = getAsServletResponse(
                "wfs3/styles/dashed?f=sld");
        assertEquals(HttpStatus.OK.value(), response.getStatus());
        assertEquals(SLDHandler.MIMETYPE_10, response.getContentType());
        final Document dom = dom(response, true);
        print(dom);
    }

    @Test
    public void testPostSLDStyleGlobal() throws Exception {
        String styleBody = loadStyle("simplePoint.sld");
        final MockHttpServletResponse response =
                postAsServletResponse("wfs3/styles", styleBody, SLDHandler.MIMETYPE_10);
        assertEquals(201, response.getStatus());
        assertEquals(
                "http://localhost:8080/geoserver/wfs3/styles/simplePoint",
                response.getHeader(HttpHeaders.LOCATION));

        // check style creation
        final StyleInfo styleInfo = getCatalog().getStyleByName("simplePoint");
        checkSimplePoint(styleInfo);
    }

    public void checkSimplePoint(StyleInfo styleInfo) throws IOException {
        assertNotNull(styleInfo);
        final Style style = styleInfo.getStyle();
        PointSymbolizer ps =
                (PointSymbolizer)
                        style.featureTypeStyles().get(0).rules().get(0).symbolizers().get(0);
        final Mark mark = (Mark) ps.getGraphic().graphicalSymbols().get(0);
        assertEquals("circle", mark.getWellKnownName().evaluate(null, String.class));
    }

    @Test
    public void testPostSLDStyleInWorkspace() throws Exception {
        String styleBody = loadStyle("simplePoint.sld");
        final MockHttpServletResponse response =
                postAsServletResponse("cite/wfs3/styles", styleBody, SLDHandler.MIMETYPE_10);
        assertEquals(201, response.getStatus());
        assertEquals(
                "http://localhost:8080/geoserver/cite/wfs3/styles/simplePoint",
                response.getHeader(HttpHeaders.LOCATION));

        final StyleInfo styleInfo = getCatalog().getStyleByName("cite", "simplePoint");
        checkSimplePoint(styleInfo);
    }

    public String loadStyle(String fileName) throws IOException {
        try (InputStream is = StyleTest.class.getResourceAsStream(fileName)) {
            return IOUtils.toString(is, "UTF-8");
        }
    }
}
