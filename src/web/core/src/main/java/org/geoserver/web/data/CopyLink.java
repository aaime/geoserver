/* (c) 2014 - 2016 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web.data;

import java.util.List;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogCloneVisitor;
import org.geoserver.catalog.CatalogInfo;
import org.geoserver.web.GeoServerApplication;
import org.geoserver.web.wicket.GeoServerTablePanel;

/**
 * A reusable recursive, multiple copy link. Assumes the presence of a table panel filled with
 * catalog objects
 */
@SuppressWarnings("serial")
public class CopyLink extends AjaxLink<Void> {

    GeoServerTablePanel<? extends CatalogInfo> catalogObjects;

    public CopyLink(String id, GeoServerTablePanel<? extends CatalogInfo> catalogObjects) {
        super(id);
        this.catalogObjects = catalogObjects;
    }

    @Override
    public void onClick(AjaxRequestTarget target) {
        // see if the user selected anything
        final List<? extends CatalogInfo> selection = catalogObjects.getSelection();
        if (selection.size() == 0) return;

        Catalog catalog = GeoServerApplication.get().getCatalog();
        CatalogCloneVisitor cloner = new CatalogCloneVisitor(catalog, true);
        for (CatalogInfo catalogInfo : selection) {
            catalogInfo.accept(cloner);
        }

        catalogObjects.clearSelection();
        target.add(catalogObjects);
    }
}
