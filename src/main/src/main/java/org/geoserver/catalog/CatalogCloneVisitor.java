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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A {@link CatalogVisitor} that can be used to copy {@link CatalogInfo} objects, eventually in a
 * recursive way.
 */
public class CatalogCloneVisitor implements CatalogVisitor {

    public static final String DEFAULT_COPY_PREFIX = "CopyOf";
    private final boolean recursive;
    Catalog catalog;
    private String prefix = DEFAULT_COPY_PREFIX;
    private WorkspaceInfo targetWorkspace;
    private StoreInfo targetStore;

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

    // TODO: add workspace cloning with a flag to turn off prefix and look for copied
    // resources in groups -> layers, styles and groups, layers -> styles, layers -> resources.
    // copy order is workspace, styles, resources, layers, groups ordered by dependency.
    // probably to be done iteratively rathern than recursively?

    @Override
    public void visit(WorkspaceInfo workspace) {
        WorkspaceInfo target = catalog.getFactory().createWorkspace();
        copy(WorkspaceInfo.class, workspace, target);
        catalog.add(target);
        catalog.getNamespaceByPrefix(workspace.getName()).accept(this);

        if (recursive) {
            try {
                this.targetWorkspace = target;
                // styles
                for (StyleInfo style : catalog.getStylesByWorkspace(workspace)) {
                    style.accept(this);
                }

                // stores
                for (StoreInfo s : catalog.getStoresByWorkspace(workspace, StoreInfo.class)) {
                    s.accept(this);
                }

                // groups
                List<LayerGroupInfo> groups = catalog.getLayerGroupsByWorkspace(workspace);
                Collections.sort(groups, new LayerGroupComparator());
                for (LayerGroupInfo group : groups) {
                    group.accept(this);
                }
            } finally {
                this.targetWorkspace = null;
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

        if (recursive) {
            try {
                targetStore = target;
                recurseOnStore(dataStore);
            } finally {
                targetStore = null;
            }
        }
    }

    @Override
    public void visit(CoverageStoreInfo coverageStore) {
        CoverageStoreInfo target = catalog.getFactory().createCoverageStore();
        copy(CoverageStoreInfo.class, coverageStore, target);
        catalog.add(target);

        if (recursive) {
            try {
                targetStore = target;
                recurseOnStore(coverageStore);
            } finally {
                targetStore = null;
            }
        }
    }

    @Override
    public void visit(WMSStoreInfo wmsStore) {
        WMSStoreInfo target = catalog.getFactory().createWebMapServer();
        copy(WMSStoreInfo.class, wmsStore, target);
        catalog.add(target);

        if (recursive) {
            try {
                targetStore = target;
                recurseOnStore(wmsStore);
            } finally {
                targetStore = null;
            }
        }
    }

    @Override
    public void visit(WMTSStoreInfo wmtsStore) {
        WMTSStoreInfo target = catalog.getFactory().createWebMapTileServer();
        copy(WMTSStoreInfo.class, wmtsStore, target);
        catalog.add(target);

        if (recursive) {
            try {
                targetStore = target;
                recurseOnStore(wmtsStore);
            } finally {
                targetStore = null;
            }
        }
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

        // Hndle style references during workspace copy. Added some null safety for broken
        // configurations, such as, null default style, or layer referecing a workspaces style that
        // is not in the same workspace
        if (targetWorkspace != null) {
            StyleInfo defaultStyle = target.getDefaultStyle();
            if (defaultStyle != null && defaultStyle.getWorkspace() != null) {
                StyleInfo defaultStyleCopy =
                        catalog.getStyleByName(targetWorkspace.getName(), defaultStyle.getName());
                if (defaultStyleCopy != null) {
                    target.setDefaultStyle(defaultStyleCopy);
                }
            }

            Set<StyleInfo> styles = target.getStyles();
            if (styles != null) {
                Set<StyleInfo> adjustedStyles = new LinkedHashSet<>();
                for (StyleInfo style : styles) {
                    if (style == null || style.getWorkspace() == null) {
                        adjustedStyles.add(style);
                    }
                    StyleInfo styleCopy =
                            catalog.getStyleByName(targetWorkspace.getName(), style.getName());
                    if (styleCopy != null) {
                        adjustedStyles.add(styleCopy);
                    } else {
                        adjustedStyles.add(style);
                    }
                }
            }
        }

        catalog.add(target);
    }

    @Override
    public void visit(StyleInfo style) {
        StyleInfo target = catalog.getFactory().createStyle();
        copy(StyleInfo.class, style, target);
        if (targetWorkspace != null) {
            target.setWorkspace(targetWorkspace);
        }

        // cloning the style info is not enough, we need to make a copy of the style itself
        try {
            GeoServerDataDirectory dd = new GeoServerDataDirectory(catalog.getResourceLoader());
            Resource styleResource = dd.style(style);
            // if a ws is being copied, the target file is in the target workspace
            Resource parent;
            if (targetWorkspace != null) {
                parent = dd.getStyles(targetWorkspace);
            } else {
                parent = styleResource.parent();
            }
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

            // in case there are referred resources, they need to be copied over too
            if (targetWorkspace != null) {
                // look for any resource files (image, etc...) and copy them over, don't move
                // since they could be shared among other styles
                Resource oldDir = dd.getStyles(style.getWorkspace());
                Resource newDir = dd.getStyles(targetWorkspace);
                URI oldDirURI = new URI(oldDir.path());
                for (Resource old : dd.additionalStyleResources((StyleInfo) style)) {
                    if (old.getType() != Resource.Type.UNDEFINED) {
                        URI oldURI = new URI(old.path());
                        final URI relative = oldDirURI.relativize(oldURI);
                        final Resource targetDirectory = newDir.get(relative.getPath()).parent();
                        if (targetDirectory.getType() == Resource.Type.UNDEFINED)
                            targetDirectory.dir();
                        Resources.copy(old, targetDirectory);
                    }
                }
            }
        } catch (IOException | URISyntaxException e) {
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
        if (!(source instanceof LayerGroupInfo)) {
            OwsUtils.set(target, "id", null);
        }
        // change the name in case of copy in the same workspace, otherwise set the workspace
        if (targetWorkspace == null) {
            setUniqueName(clazz, source, target);
        } else if (OwsUtils.setter(clazz, "workspace", WorkspaceInfo.class) != null) {
            OwsUtils.set(target, "workspace", targetWorkspace);
        } else if (targetStore != null && target instanceof ResourceInfo) {
            ResourceInfo targetResource = (ResourceInfo) target;
            targetResource.setStore(targetStore);
            targetResource.setNamespace(catalog.getNamespaceByPrefix(targetWorkspace.getName()));
        }
        Date date = new Date();
        target.setDateCreated(date);
        target.setDateModified(date);

        return target;
    }

    private <T extends CatalogInfo> void setUniqueName(Class<T> clazz, T source, T target) {
        if (source instanceof NamespaceInfo) {
            // uses prefix instead of name
            NamespaceInfo sourceNs = (NamespaceInfo) source;
            NamespaceInfo targetNs = (NamespaceInfo) target;
            String newName = prefix + sourceNs.getName();
            int i = 2;
            while (catalog.get(clazz, Predicates.equal("name", newName)) != null) {
                newName = prefix + sourceNs.getName() + i;
                i++;
            }
            targetNs.setPrefix(newName);

            // uri must be unique too
            String newURI = sourceNs.getURI() + 2;
            i = 3;
            while (catalog.getNamespaceByURI(newURI) != null) {
                newURI = sourceNs.getURI() + i;
                i++;
            }
            targetNs.setURI(newURI);
        } else {
            String newName = prefix + OwsUtils.get(source, "name");
            int i = 2;
            while (catalog.get(clazz, Predicates.equal("name", newName)) != null) {
                newName = prefix + OwsUtils.get(source, "name") + i;
                i++;
            }

            OwsUtils.set(target, "name", newName);
        }
    }

    /**
     * Comparator sorting groups in order of dependency, leaving those with dependencies after the
     * ones they depend onto. The class assumes there are no cicular dependencies.
     */
    static class LayerGroupComparator implements Comparator<LayerGroupInfo> {

        static LayerGroupComparator INSTANCE = new LayerGroupComparator();

        private LayerGroupComparator() {};

        @Override
        public int compare(LayerGroupInfo a, LayerGroupInfo b) {
            List<LayerGroupInfo> aGroups = new LayerGroupHelper(a).allGroups();
            if (aGroups.contains(b)) {
                return -1;
            }
            List<LayerGroupInfo> bGroups = new LayerGroupHelper(a).allGroups();
            if (bGroups.contains(a)) {
                return 1;
            }
            return 0;
        }
    }
}
