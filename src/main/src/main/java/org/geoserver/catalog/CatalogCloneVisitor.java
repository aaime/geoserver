/* (c) 2020 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog;

import com.google.common.base.Strings;

import org.apache.commons.io.FilenameUtils;
import org.geoserver.catalog.impl.WMSLayerInfoImpl;
import org.geoserver.config.GeoServerDataDirectory;
import org.geoserver.ows.util.OwsUtils;
import org.geoserver.platform.resource.Resource;
import org.geoserver.platform.resource.Resources;
import org.geowebcache.layer.wms.WMSLayer;

import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * A {@link CatalogVisitor} that can be used to copy {@link CatalogInfo} objects, eventually in a
 * recursive way.
 */
public class CatalogCloneVisitor implements CatalogVisitor {

    private final boolean recursive;
    Catalog catalog;
    private String prefix = "CopyOf";

    /**
     * Creates a new non recursive cloner. Also see {@link
     * CatalogCloneVisitor#CatalogCloneVisitor(Catalog, boolean)}.
     *
     * @param catalog The catalog that will contain the copies, and whose factory is used to create
     *     new objects
     */
    public CatalogCloneVisitor(Catalog catalog) {
        this(catalog, false);
    }

    /**
     * Creates a new cloner
     *
     * @param catalog The catalog that will contain the copies, and whose factory is used to create
     *     new objects
     * @param recursive Whether the copy should be recursive, or shallo
     */
    public CatalogCloneVisitor(Catalog catalog, boolean recursive) {
        this.catalog = catalog;
        this.recursive = recursive;
    }

    /** Gets the prefix assigned to clone names (defaults to "CopyOf") */
    public String getPrefix() {
        return prefix;
    }

    /**
     * Sets the prefix assigned to clone names. Ideally it should be short, without spaces and
     * uncommon chars, as it may become part of a service visible resource
     *
     * @param prefix
     */
    public void setPrefix(String prefix) {
        if (Strings.isNullOrEmpty(prefix))
            throw new IllegalArgumentException("The prefix must be non null and not empty");
        this.prefix = prefix;
    }

    @Override
    public void visit(Catalog catalog) {
        throw new RuntimeException("No support for cloning an entire catalog");
    }

    @Override
    public void visit(WorkspaceInfo workspace) {
        WorkspaceInfo target = catalog.getFactory().createWorkspace();
        copy(WorkspaceInfo.class, workspace, target);
        catalog.add(target);
        catalog.getNamespaceByPrefix(workspace.getName()).accept(this);

        if (recursive) {
            // stores
            for (StoreInfo s : catalog.getStoresByWorkspace(workspace, StoreInfo.class)) {
                s.accept(this);
            }

            // styles
            for (StyleInfo style : catalog.getStylesByWorkspace(workspace)) {
                style.accept(this);
            }

            // groups
            for (LayerGroupInfo group : catalog.getLayerGroupsByWorkspace(workspace)) {
                group.accept(this);
            }
        }
    }

    @Override
    public void visit(NamespaceInfo ns) {
        NamespaceInfo target = catalog.getFactory().createNamespace();
        copy(NamespaceInfo.class, ns, target);
        catalog.add(target);
    }

    void recurseOnStore(StoreInfo store) {
        // drill down into owned resources/layers
        List<ResourceInfo> resources = catalog.getResourcesByStore(store, ResourceInfo.class);
        for (ResourceInfo ri : resources) {
            List<LayerInfo> layers = catalog.getLayers(ri);
            if (!layers.isEmpty()) {
                for (LayerInfo li : layers) {
                    li.accept(this);
                }
            } else {
                // no layers for the resource, delete directly
                ri.accept(this);
            }
        }
    }

    @Override
    public void visit(DataStoreInfo dataStore) {
        DataStoreInfo target = catalog.getFactory().createDataStore();
        copy(DataStoreInfo.class, dataStore, target);
        catalog.add(target);

        if (recursive) recurseOnStore(dataStore);
    }

    @Override
    public void visit(CoverageStoreInfo coverageStore) {
        CoverageStoreInfo target = catalog.getFactory().createCoverageStore();
        copy(CoverageStoreInfo.class, coverageStore, target);
        catalog.add(target);

        if (recursive) recurseOnStore(coverageStore);
    }

    @Override
    public void visit(WMSStoreInfo wmsStore) {
        WMSStoreInfo target = catalog.getFactory().createWebMapServer();
        copy(WMSStoreInfo.class, wmsStore, target);
        catalog.add(target);

        if (recursive) recurseOnStore(wmsStore);
    }

    @Override
    public void visit(WMTSStoreInfo wmtsStore) {
        WMTSStoreInfo target = catalog.getFactory().createWebMapTileServer();
        copy(WMTSStoreInfo.class, wmtsStore, target);
        catalog.add(target);

        if (recursive) recurseOnStore(wmtsStore);
    }

    @Override
    public void visit(FeatureTypeInfo featureType) {
        FeatureTypeInfo target = catalog.getFactory().createFeatureType();
        copy(FeatureTypeInfo.class, featureType, target);
        catalog.add(target);
    }

    @Override
    public void visit(CoverageInfo coverage) {
        CoverageInfo target = catalog.getFactory().createCoverage();
        copy(CoverageInfo.class, coverage, target);
        catalog.add(target);
    }

    @Override
    public void visit(LayerInfo layer) {
        LayerInfo target = catalog.getFactory().createLayer();
        OwsUtils.copy(layer, target, LayerInfo.class);
        OwsUtils.set(target, "id", null);
        // avoid issues with automatic renaming of resources after the layer name
        target.setResource(null);  

        // copying the resource is mandatory, they must have the same name, so here
        // we go off-pattern, need the copy to set
        ResourceInfo resource = layer.getResource();
        if (resource != null) {
            ResourceInfo resourceCopy;
            if (resource instanceof FeatureTypeInfo) {
                resourceCopy =
                        copy(
                                FeatureTypeInfo.class,
                                (FeatureTypeInfo) resource,
                                catalog.getFactory().createFeatureType());
            } else if (resource instanceof CoverageInfo) {
                resourceCopy =
                        copy(
                                CoverageInfo.class,
                                (CoverageInfo) resource,
                                catalog.getFactory().createCoverage());
            } else if (resource instanceof WMSLayerInfo) {
                resourceCopy =
                        copy(
                                WMSLayerInfo.class,
                                (WMSLayerInfo) resource,
                                catalog.getFactory().createWMSLayer());
            } else if (resource instanceof WMTSLayerInfo) {
                resourceCopy =
                        copy(
                                WMTSLayerInfo.class,
                                (WMTSLayerInfo) resource,
                                catalog.getFactory().createWMTSLayer());
            } else {
                throw new IllegalArgumentException(
                        "Unrecognized resource type " + resource + ", please extend this method");
            }
            target.setResource(resourceCopy);
            catalog.add(resourceCopy);
        }
        catalog.add(target);
    }

    @Override
    public void visit(StyleInfo style) {
        StyleInfo target = catalog.getFactory().createStyle();
        copy(StyleInfo.class, style, target);

        // cloning the style info is not enough, we need to make a copy of the style itself
        try {
            GeoServerDataDirectory dd = new GeoServerDataDirectory(catalog.getResourceLoader());
            Resource styleResource = dd.style(style);
            Resource parent = styleResource.parent();
            String extension = FilenameUtils.getExtension(style.getFilename());
            Resource styleResourceCopy =
                    parent.get(target.getName() + (extension != null ? "." + extension : ""));
            int i = 2;
            while (styleResourceCopy.getType() != Resource.Type.UNDEFINED) {
                styleResourceCopy =
                        parent.get(target.getName() + i + extension != null ? "." + extension : "");
                i++;
            }
            Resources.copy(styleResource, styleResourceCopy);
            target.setFilename(styleResourceCopy.name());

            // now we can copy the style object too
            catalog.add(target);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void visit(LayerGroupInfo layerGroup) {
        LayerGroupInfo target = catalog.getFactory().createLayerGroup();
        copy(LayerGroupInfo.class, layerGroup, target);
        catalog.add(target);
    }

    @Override
    public void visit(WMSLayerInfo wmsLayer) {
        WMSLayerInfo target = catalog.getFactory().createWMSLayer();
        copy(WMSLayerInfo.class, wmsLayer, target);
        catalog.add(target);
    }

    @Override
    public void visit(WMTSLayerInfo layer) {
        WMTSLayerInfo target = catalog.getFactory().createWMTSLayer();
        copy(WMTSLayerInfo.class, layer, target);
        catalog.add(target);
    }

    protected <T extends CatalogInfo> T copy(Class<T> clazz, T source, T target) {
        if (source instanceof WMSLayerInfo) {
            // some getters actually work against the store, there is order dependency here
            ((WMSLayerInfoImpl) target).setStore(((WMSLayerInfo) source).getStore());
        }
        OwsUtils.copy(clazz.cast(source), clazz.cast(target), clazz);
        OwsUtils.set(target, "id", null);
        setUniqueName(clazz, source, target);
        Date date = new Date();
        target.setDateCreated(date);
        target.setDateModified(date);

        return target;
    }

    private <T extends CatalogInfo> void setUniqueName(Class<T> clazz, T source, T target) {
        String newName = prefix + OwsUtils.get(source, "name");
        int i = 2;
        while (catalog.get(clazz, Predicates.equal("name", newName)) != null) {
            newName = prefix + OwsUtils.get(source, "name") + i;
            i++;
        }
        OwsUtils.set(target, "name", newName);
    }
}
