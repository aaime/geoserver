/* (c) 2019 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.api.checkpoint;

import static org.geoserver.api.checkpoint.CheckpointIndexProvider.INITIAL_STATE;

import org.geoserver.api.APIBBoxParser;
import org.geoserver.api.APIDispatcher;
import org.geoserver.api.APIException;
import org.geoserver.api.APIService;
import org.geoserver.api.tiles.TilesServiceInfo;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.gwc.GWC;
import org.geoserver.wms.capabilities.CapabilityUtil;
import org.geotools.coverage.grid.io.StructuredGridCoverage2DReader;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.geotools.util.NumberRange;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.filter.parameters.ParameterFilter;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.opengis.filter.Filter;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.TransformException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Extension to the Tiles service allowing to retrieve checkpoints */
@APIService(
        service = "Tiles",
        version = "1.0",
        landingPage = "ogc/tiles",
        serviceClass = TilesServiceInfo.class)
@RequestMapping(path = APIDispatcher.ROOT_PATH + "/tiles")
public class CheckpointTilesService {

    public static final String CHANGESET_MIME = "application/changeset+json";
    public static final String GET_RENDERED_COLLECTION_TILES = "getRenderedCollectionTiles";
    private final CheckpointIndexProvider indexProvider;
    private final Catalog catalog;
    private final GWC gwc;

    public enum ChangeSetType {
        summary("summary"),
        pack("package");
        String name;

        ChangeSetType(String name) {
            this.name = name;
        }

        public static ChangeSetType fromName(String changeSetType) {
            if (changeSetType == null) {
                return null;
            }

            for (ChangeSetType value : values()) {
                if (changeSetType.equals(value.name)) {
                    return value;
                }
            }
            // not sure what happens with the normal converter, might result in a 500 instead of a
            // 400, no time to verify and eventually amend
            throw new APIException(
                    "IllegalParameterValue",
                    "Could not find a changeset type named " + changeSetType,
                    HttpStatus.BAD_REQUEST);
        }
    }

    public CheckpointTilesService(GWC gwc, CheckpointIndexProvider indexProvider, Catalog catalog) {
        this.gwc = gwc;
        this.indexProvider = indexProvider;
        this.catalog = catalog;
    }

    @GetMapping(
            path = "/collections/{collectionId}/map/{styleId}/tiles/{tileMatrixSetId}",
            name = GET_RENDERED_COLLECTION_TILES,
            produces = CHANGESET_MIME)
    @ResponseBody
    public Object getMultiTiles(
            @PathVariable(name = "collectionId") String collectionId,
            @PathVariable(name = "styleId") String styleId,
            @PathVariable(name = "tileMatrixSetId") String tileMatrixSetId,
            @RequestParam(name = "scaleDenominator", required = false) String scaleDenominatorSpec,
            @RequestParam(name = "bbox", required = false) String bboxSpec,
            @RequestParam(name = "f-tile", required = false) String tileFormat,
            @RequestParam(name = "checkPoint", required = false, defaultValue = INITIAL_STATE)
                    String checkpoint,
            @RequestParam(name = "changeSetType", required = false) String changeSetTypeName)
            throws GeoWebCacheException, IOException, NoSuchAlgorithmException, FactoryException {
        ChangeSetType changeSetType = ChangeSetType.fromName(changeSetTypeName);
        Filter spatialFilter = APIBBoxParser.toFilter(bboxSpec);
        // collection must be a structured coverage and a tile layer at the same time
        CoverageInfo ci = getStructuredCoverageInfo(collectionId, true);
        TileLayer tileLayer = getTileLayer(collectionId);
        validateStyle(tileLayer, styleId);
        validateGridset(tileLayer, tileMatrixSetId);
        GridSet gridset = getGridset(tileMatrixSetId);
        // get (and check) the style too
        StyleInfo style = getStyle(styleId);

        // get the changed areas
        SimpleFeatureCollection areas =
                indexProvider.getModifiedAreas(ci, checkpoint, spatialFilter);
        if ((areas == null || areas.isEmpty())) {
            if (!INITIAL_STATE.equals(checkpoint)) {
                throw new APIException(
                        "NoChanges",
                        "No changes occurred since checkpoint",
                        HttpStatus.NOT_MODIFIED);
            }
        }

        // compute the changed bboxes
        List<ReferencedEnvelope> extentOfChangedItems = new ArrayList<>();
        try (SimpleFeatureIterator fi = areas.features()) {
            while (fi.hasNext()) {
                // TODO: if they are multipolygons, would make sense to split them
                Envelope envelope =
                        ((Geometry) fi.next().getDefaultGeometry()).getEnvelopeInternal();
                ReferencedEnvelope re =
                        new ReferencedEnvelope(
                                envelope, areas.getSchema().getCoordinateReferenceSystem());
                CoordinateReferenceSystem gridsetCRS =
                        CRS.decode("EPSG:" + gridset.getSrs().getNumber(), true);
                try {
                    // TODO: might want to use the projection handler to avoid impossible
                    // reprojections
                    ReferencedEnvelope boundsInGridsetCRS = re.transform(gridsetCRS, true);
                    extentOfChangedItems.add(boundsInGridsetCRS);
                } catch (TransformException e) {
                    throw new APIException(
                            "InternalError",
                            "Failed to reproject extent of changed items to gridset crs",
                            HttpStatus.INTERNAL_SERVER_ERROR);
                }
            }
        }

        NumberRange<Double> scaleRange =
                CapabilityUtil.searchMinMaxScaleDenominator(Collections.singleton(style));
        ModifiedTiles modifiedTiles =
                new ModifiedTiles(
                        ci,
                        tileLayer,
                        tileLayer.getGridSubset(tileMatrixSetId),
                        areas,
                        APIBBoxParser.parse(bboxSpec),
                        scaleRange);
        ChangeSet changeSet =
                new ChangeSet(checkpoint, extentOfChangedItems, modifiedTiles.getModifiedTiles());
        changeSet.setScaleOfChangedItems(scaleRange);
        return changeSet;
    }

    public StyleInfo getStyle(@PathVariable(name = "styleId") String styleId) {
        StyleInfo styleInfo = catalog.getStyleByName(styleId);
        if (styleInfo == null) {
            throw new APIException(
                    "NotFound", "Could not locate style " + styleId, HttpStatus.NOT_FOUND);
        }
        return styleInfo;
    }

    CoverageInfo getStructuredCoverageInfo(String collectionId, boolean failIfNotFound)
            throws IOException {
        org.geoserver.catalog.CoverageInfo coverageInfo = catalog.getCoverageByName(collectionId);
        if (coverageInfo != null
                && coverageInfo.getGridCoverageReader(null, null)
                        instanceof StructuredGridCoverage2DReader) {
            return coverageInfo;
        }

        if (failIfNotFound) {
            throw new APIException(
                    "NotFound",
                    "Could not locate collection " + collectionId,
                    HttpStatus.NOT_FOUND);
        } else {
            return null;
        }
    }

    private TileLayer getTileLayer(String collectionId) {
        try {
            return gwc.getTileLayerByName(collectionId);
        } catch (IllegalArgumentException e) {
            throw new APIException(
                    "InvalidParameter",
                    "Tiled collection " + collectionId + " not found",
                    HttpStatus.NOT_FOUND,
                    e);
        }
    }

    private void validateStyle(TileLayer tileLayer, String styleId) {
        // is it the default style? if so, nothing to check
        if (styleId.equalsIgnoreCase(tileLayer.getStyles())) {
            return;
        }
        // look for the other possible values
        Optional<ParameterFilter> styles =
                tileLayer.getParameterFilters().stream()
                        .filter(pf -> "styles".equalsIgnoreCase(pf.getKey()))
                        .findFirst();
        if (!styles.isPresent() && !styles.get().applies(styleId)) {
            throw new APIException(
                    "InvalidParameterValue",
                    "Invalid style name, please check the collection description for valid style names: "
                            + tileLayer.getStyles(),
                    HttpStatus.BAD_REQUEST);
        }
    }

    private GridSet getGridset(String tileMatrixSetId) {
        GridSet gridSet = gwc.getGridSetBroker().get(tileMatrixSetId);
        if (gridSet == null) {
            throw new APIException(
                    "NotFound",
                    "Tile matrix set " + tileMatrixSetId + " not recognized",
                    HttpStatus.NOT_FOUND);
        }

        return gridSet;
    }

    private void validateGridset(TileLayer tileLayer, String tileMatrixSetId) {
        GridSubset gridSubset = tileLayer.getGridSubset(tileMatrixSetId);
        if (gridSubset == null) {
            throw new APIException(
                    "InvalidParameterValue",
                    "Invalid tileMatrixSetId " + tileMatrixSetId,
                    HttpStatus.BAD_REQUEST);
        }
    }
}
