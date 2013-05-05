package org.geoserver.data;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.geoserver.catalog.Catalog;
import org.geoserver.platform.GeoServerExtensions;
import org.geotools.data.store.ContentDataStore;
import org.geotools.data.store.ContentEntry;
import org.geotools.data.store.ContentFeatureSource;
import org.geotools.feature.NameImpl;
import org.opengis.feature.type.Name;

public class CatalogStore extends ContentDataStore {

    @Override
    protected List<Name> createTypeNames() throws IOException {
        return Collections.singletonList((Name) new NameImpl(namespaceURI, "layers"));
    }

    @Override
    protected ContentFeatureSource createFeatureSource(ContentEntry entry) throws IOException {
        Catalog catalog = (Catalog) GeoServerExtensions.bean("catalog");
        return new LayersFeatureSource(entry, catalog);
    }

}
