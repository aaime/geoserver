/* (c) 2021 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.ogcapi.maps;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.PublishedInfo;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.config.GeoServer;
import org.geoserver.ogcapi.APIBBoxParser;
import org.geoserver.ogcapi.APIDispatcher;
import org.geoserver.ogcapi.APIException;
import org.geoserver.ogcapi.APIService;
import org.geoserver.ogcapi.ConformanceClass;
import org.geoserver.ogcapi.ConformanceDocument;
import org.geoserver.ogcapi.HTMLResponseBody;
import org.geoserver.ogcapi.StyleDocument;
import org.geoserver.platform.ServiceException;
import org.geoserver.wms.GetMapRequest;
import org.geoserver.wms.MapLayerInfo;
import org.geoserver.wms.WMSInfo;
import org.geoserver.wms.WebMap;
import org.geoserver.wms.WebMapService;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.opengis.referencing.FactoryException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.awt.Color;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@APIService(
        service = "Maps",
        version = "1.0",
        landingPage = "ogc/maps",
        serviceClass = WMSInfo.class)
@RequestMapping(path = APIDispatcher.ROOT_PATH + "/maps")
public class MapsService {

    public static final String CORE = "http://www.opengis.net/spec/ogcapi-maps-1/1.0/req/core";
    public static final String GEODATA =
            "http://www.opengis.net/spec/ogcapi-maps-1/1.0/conf/geodata";

    private static final String DISPLAY_NAME = "OGC API Maps";

    private final GeoServer geoServer;
    private final WebMapService wms;

    public MapsService(GeoServer geoServer, @Qualifier("wmsService2") WebMapService wms) {
        this.geoServer = geoServer;
        this.wms = wms;
    }

    public WMSInfo getService() {
        return geoServer.getService(WMSInfo.class);
    }

    private Catalog getCatalog() {
        return geoServer.getCatalog();
    }

    @GetMapping(name = "getLandingPage")
    @ResponseBody
    @HTMLResponseBody(templateName = "landingPage.ftl", fileName = "landingPage.html")
    public MapsLandingPage landingPage() {
        return new MapsLandingPage(getService(), getCatalog(), "ogc/maps");
    }

    @GetMapping(path = "conformance", name = "getConformanceDeclaration")
    @ResponseBody
    @HTMLResponseBody(templateName = "conformance.ftl", fileName = "conformance.html")
    public ConformanceDocument conformance() {
        List<String> classes =
                Arrays.asList(ConformanceClass.CORE, ConformanceClass.COLLECTIONS, CORE, GEODATA);
        return new ConformanceDocument(DISPLAY_NAME, classes);
    }

    @GetMapping(path = "collections", name = "getCollections")
    @ResponseBody
    @HTMLResponseBody(templateName = "collections.ftl", fileName = "collections.html")
    public CollectionsDocument getCollections() {
        return new CollectionsDocument(geoServer);
    }

    @GetMapping(path = "collections/{collectionId}", name = "describeCollection")
    @ResponseBody
    @HTMLResponseBody(templateName = "collection.ftl", fileName = "collection.html")
    public CollectionDocument collection(@PathVariable(name = "collectionId") String collectionId) {
        PublishedInfo p = getPublished(collectionId);
        CollectionDocument collection = new CollectionDocument(geoServer, p);

        return collection;
    }

    @GetMapping(path = "collections/{collectionId}/styles", name = "getStyles")
    @ResponseBody
    @HTMLResponseBody(templateName = "styles.ftl", fileName = "styles.html")
    public StylesDocument styles(@PathVariable(name = "collectionId") String collectionId) {
        PublishedInfo p = getPublished(collectionId);
        return new StylesDocument(p);
    }

    private PublishedInfo getPublished(String collectionId) {
        // single collection
        PublishedInfo p = getCatalog().getLayerByName(collectionId);
        if (p == null) getCatalog().getLayerGroupByName(collectionId);

        if (p == null)
            throw new ServiceException(
                    "Unknown collection " + collectionId,
                    ServiceException.INVALID_PARAMETER_VALUE,
                    "collectionId");

        return p;
    }

    @GetMapping(path = "collections/{collectionId}/styles/{styleId}/map", name = "getCollectionMap")
    @ResponseBody
    public WebMap map(
            @PathVariable(name = "collectionId") String collectionId,
            @PathVariable(name = "styleId") String styleId,
            @RequestParam(name = "f") String format,
            @RequestParam(name = "bbox", required = false) String bbox,
            @RequestParam(name = "crs", required = false) String bboxCRS,
            @RequestParam(name = "width", required = false) Integer width,
            @RequestParam(name = "height", required = false) Integer height,
            @RequestParam(name = "transparent", required = false, defaultValue = "true")
                    boolean transparent,
            @RequestParam(name = "bgcolor", required = false) String bgcolor
            // TODO: add all the vendor parameters we normally support in WMS
            ) throws IOException, FactoryException {
        PublishedInfo p = getPublished(collectionId);
        checkStyle(p, styleId);
        StyleInfo styleInfo = getCatalog().getStyleByName(styleId);

        GetMapRequest request = new GetMapRequest();
        request.setLayers(getMapLayers(p));
        request.setStyles(Arrays.asList(styleInfo.getStyle()));
        request.setFormat(format);
        if (bbox != null) {
            ReferencedEnvelope[] parsed = APIBBoxParser.parse(bbox, bboxCRS);
            if (parsed.length > 1)
                throw new APIException(
                        ServiceException.INVALID_PARAMETER_VALUE,
                        "Cannot handle dateline crossing requests",
                        HttpStatus.BAD_REQUEST);
            request.setBbox(parsed[0]);
        }
        if (width != null) request.setWidth(width);
        if (height != null) request.setHeight(height);
        if (bgcolor != null) request.setBgColor(Color.decode(bgcolor));
        request.setTransparent(transparent);
        // TODO: add other parameters
        return wms.reflect(request);
    }

    private List<MapLayerInfo> getMapLayers(PublishedInfo p) {
        if (p instanceof LayerGroupInfo) {
            return ((LayerGroupInfo) p)
                    .layers().stream().map(l -> new MapLayerInfo(l)).collect(Collectors.toList());
        } else if (p instanceof LayerInfo) {
            return Arrays.asList(new MapLayerInfo((LayerInfo) p));
        } else {
            throw new RuntimeException("Unexpected published object" + p);
        }
    }

    private void checkStyle(PublishedInfo p, String styleId) {
        if (p instanceof LayerGroupInfo && StyleDocument.DEFAULT_STYLE_NAME.equals(styleId)) {
            return;
        } else if (p instanceof LayerInfo) {
            LayerInfo l = (LayerInfo) p;
            if (l.getDefaultStyle().prefixedName().equals(styleId)
                    || l.getStyles().stream().anyMatch(s -> s.prefixedName().equals(styleId)))
                return;
        } else {
            throw new RuntimeException("Unexpected published object" + p);
        }
        // in any other case, the style was not recognized
        throw new APIException(
                ServiceException.INVALID_PARAMETER_VALUE,
                "Invalid style identifier: " + styleId,
                HttpStatus.BAD_REQUEST);
    }
}