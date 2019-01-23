/* (c) 2018 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wfs3;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import java.io.InputStream;
import java.util.Collections;
import org.geoserver.ows.URLMangler;
import org.geoserver.ows.util.ResponseUtils;
import org.geoserver.util.IOUtils;
import org.geoserver.wfs3.response.CollectionDocument;
import org.geoserver.wfs3.response.Link;

/** WFS3 extension adding support for get/put styles (as per OGC VTP pilot) */
public class StylesExtension extends AbstractWFS3Extension {

    private static final String STYLES_SPECIFICATION;
    static final String STYLES_PATH = "/styles";
    static final String STYLE_PATH = "/styles/{styleId}";
    static final String COLLECTION_STYLES_PATH = "/collections/{collectionId}/styles";
    static final String COLLECTION_STYLE_PATH = "/collections/{collectionId}/styles/{styleId}";

    static {
        try (InputStream is = StylesExtension.class.getResourceAsStream("styles.yml")) {
            STYLES_SPECIFICATION = IOUtils.toString(is);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read the styles.yaml template", e);
        }
    }

    @Override
    public void extendAPI(OpenAPI api) {
        // load the pre-cooked building blocks
        OpenAPI styleAPI = readTemplate(StylesExtension.STYLES_SPECIFICATION);

        // customize paths
        Paths paths = api.getPaths();
        paths.addPathItem(STYLES_PATH, styleAPI.getPaths().get(STYLES_PATH));
        paths.addPathItem(STYLE_PATH, styleAPI.getPaths().get(STYLE_PATH));
        paths.addPathItem(COLLECTION_STYLES_PATH, styleAPI.getPaths().get(COLLECTION_STYLES_PATH));
        paths.addPathItem(COLLECTION_STYLE_PATH, styleAPI.getPaths().get(COLLECTION_STYLE_PATH));

        addSchemasAndParameters(api, styleAPI);
    }

    @Override
    public void extendCollection(CollectionDocument collection, BaseRequest request) {
        String collectionId = collection.getName();

        // links
        String baseUrl = request.getBaseUrl();
        String styleAPIUrl =
                ResponseUtils.buildURL(
                        baseUrl,
                        "wfs3/collections/" + collectionId + "/styles",
                        Collections.emptyMap(),
                        URLMangler.URLType.SERVICE);
        collection
                .getLinks()
                .add(
                        new Link(
                                styleAPIUrl,
                                "styles",
                                "application/json",
                                collectionId + " associated styles."));
    }
}
