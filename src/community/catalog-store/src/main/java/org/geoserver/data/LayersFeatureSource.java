package org.geoserver.data;

import java.io.IOException;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.util.CloseableIterator;
import org.geotools.data.FeatureReader;
import org.geotools.data.Query;
import org.geotools.data.store.ContentEntry;
import org.geotools.data.store.ContentFeatureSource;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.vividsolutions.jts.geom.Polygon;

public class LayersFeatureSource extends ContentFeatureSource {

    private Catalog catalog;
    private CoordinateReferenceSystem crs;

    public LayersFeatureSource(ContentEntry entry, Catalog catalog) throws IOException {
        super(entry, Query.ALL);
        this.catalog = catalog;
        try {
            this.crs = CRS.decode("EPSG:4326", true);
        } catch (Exception e) {
            throw new IOException("Failed to build the layers feature type, this is unexpected", e);
        }
    }

    @Override
    protected ReferencedEnvelope getBoundsInternal(Query query) throws IOException {
        // no optimized bounds for the moment, once we have filter mapping we can do one
        return null;
    }

    @Override
    protected int getCountInternal(Query query) throws IOException {
        // no optimized count for the moment, once we have filter mapping we can do one 
        return -1;
    }
    
//    @Override
//    protected boolean canFilter() {
//        return true;
//    }
//    
//    @Override
//    protected boolean canLimit() {
//        return true;
//    }
//    
//    @Override
//    protected boolean canOffset() {
//        return true;
//    }
    
    @Override
    protected FeatureReader<SimpleFeatureType, SimpleFeature> getReaderInternal(Query query)
            throws IOException {
        // TODO: figure out how to translate filters and make them actually work against the catalog (probably, bean accessors for properties 
        // plus a mapping between feature type names and attribute names
        // CloseableIterator<LayerInfo> list = catalog.list(LayerInfo.class, query.getFilter(), query.getStartIndex(), query.getMaxFeatures(), null);
        CloseableIterator<LayerInfo> list = catalog.list(LayerInfo.class, Filter.INCLUDE);
        return new LayerReader(list, buildFeatureType());
    }

    @Override
    protected SimpleFeatureType buildFeatureType() throws IOException {
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName(entry.getName());
        builder.add("workspace", String.class);
        builder.add("name", String.class);
        builder.add("title", String.class);
        builder.add("abstract", String.class);
        builder.add("bounds", Polygon.class, crs);
        return builder.buildFeatureType();
    }

}
