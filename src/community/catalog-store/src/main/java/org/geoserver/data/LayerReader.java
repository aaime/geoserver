package org.geoserver.data;

import java.io.IOException;
import java.util.NoSuchElementException;

import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.util.CloseableIterator;
import org.geotools.data.FeatureReader;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.vividsolutions.jts.geom.Polygon;

public class LayerReader implements FeatureReader<SimpleFeatureType, SimpleFeature> {

    private CloseableIterator<LayerInfo> delegate;

    private SimpleFeatureType schema;

    private SimpleFeatureBuilder builder;
    
    private LayerInfo next;

    public LayerReader(CloseableIterator<LayerInfo> delegate, SimpleFeatureType schema) {
        this.delegate = delegate;
        this.schema = schema;
        this.builder = new SimpleFeatureBuilder(schema);
    }

    public SimpleFeatureType getFeatureType() {
        return schema;
    }

    public boolean hasNext() throws IOException {
        if(next != null) {
            return true;
        }
        
        while(delegate.hasNext() && (next == null || !next.isEnabled())) {
            next = delegate.next();
        }
        return next != null;
    }

    public SimpleFeature next() throws IOException, IllegalArgumentException,
            NoSuchElementException {
        LayerInfo layer = next;
        next = null;
        builder.add(layer.getResource().getStore().getWorkspace().getName());
        builder.add(layer.getName());
        builder.add(layer.getResource().getTitle());
        builder.add(layer.getResource().getAbstract());
        ReferencedEnvelope bbox = layer.getResource().getLatLonBoundingBox();
        Polygon geometry = JTS.toGeometry(bbox);
        builder.add(geometry);

        return builder.buildFeature(schema.getTypeName() + "." + layer.getId());
    }

    public void close() throws IOException {
        delegate.close();
    }

}
