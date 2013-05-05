package org.geoserver.data;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;

import org.geotools.data.AbstractDataStoreFactory;
import org.geotools.data.DataStore;

public class CatalogStoreFactory extends AbstractDataStoreFactory {

    private static final String GEO_SERVER_CATALOG_STORE = "GeoServerCatalogStore";

    /** discriminator for this kind of store */
    public static final Param DSTYPE = new Param("dstype", String.class, "Type", true,
            GEO_SERVER_CATALOG_STORE);
    
    /** parameter for namespace of the datastore */
    public static final Param NAMESPACE = new Param("namespace", String.class, "Namespace", false);

    public DataStore createDataStore(Map<String, Serializable> params) throws IOException {
        return createNewDataStore(params);
    }

    public DataStore createNewDataStore(Map<String, Serializable> params) throws IOException {
        CatalogStore store = new CatalogStore();
        String ns = (String) NAMESPACE.lookUp(params);
        store.setNamespaceURI(ns);
        
        return store;
    }

    public String getDescription() {
        return "GeoServer layers bounds and metadata as a WMS/WFS data source";
    }

    public Param[] getParametersInfo() {
        return new Param[] { DSTYPE, NAMESPACE };
    }

    public boolean canProcess(Map params) {
        if(!super.canProcess(params)) {
            return false;
        }
        
        String type;
        try {
            type = (String) DSTYPE.lookUp(params);
            return GEO_SERVER_CATALOG_STORE.equals(type);
        } catch (IOException e) {
            return false;
        }
    }

}
