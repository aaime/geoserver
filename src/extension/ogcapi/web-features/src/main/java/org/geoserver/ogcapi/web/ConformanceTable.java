/* (c) 2025 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.ogcapi.web;

import java.util.List;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.DefaultItemReuseStrategy;
import org.apache.wicket.model.IModel;
import org.geoserver.ogcapi.APIConformance;
import org.geoserver.ogcapi.ConformanceInfo;
import org.geoserver.web.wicket.GeoServerDataProvider;
import org.geoserver.web.wicket.GeoServerTablePanel;
import org.geoserver.web.wicket.ParamResourceModel;

public class ConformanceTable extends GeoServerTablePanel<APIConformance> {

    private static final String NAME = "name";
    private static final String ENABLED = "enabled";

    public ConformanceTable(String id, ConformanceInfo<?> conformanceInfo, Component parent) {
        super(id, new ConformanceDataProvider(conformanceInfo, parent));

        // set up the table for editing
        setPageable(false);
        setOutputMarkupId(true);
        setSortable(false);
        setItemReuseStrategy(new DefaultItemReuseStrategy());
        setSelectable(false); // no selection, the editable checkboxes are a different case
        setFilterable(false);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Component getComponentForProperty(
            String id, IModel<APIConformance> itemModel, GeoServerDataProvider.Property<APIConformance> property) {
        if (ENABLED.equals(property.getName())) {
            Fragment fragment = new Fragment(id, "checkboxFragment", this);
            fragment.add(new ThreeStateCheckBox("checkbox", (IModel<Boolean>) property.getModel(itemModel)));
            return fragment;
        } else if (NAME.equals(property.getName())) {
            Label label = new Label(id, property.getModel(itemModel));
            label.add(AttributeModifier.replace("title", itemModel.getObject().getId()));
            return label;
        } else if (ConformanceDataProvider.LEVEL.equals(property)) {
            return new Label(id, getTranslated("level", property.getModel(itemModel)));
        } else if (ConformanceDataProvider.TYPE.equals(property)) {
            return new Label(id, getTranslated("type", property.getModel(itemModel)));
        }
        return null;
    }

    private String getTranslated(String prefix, IModel<?> model) {
        try {
            // force translation
            return new ParamResourceModel(prefix + "." + model.getObject(), this).getString();
        } catch (Exception e) {
            // if the translation fails, just return the model's object as a fallback
            return String.valueOf(model.getObject());
        }
    }

    /** Data provider for the conformance table, providing the conformance items and their properties. */
    private static class ConformanceDataProvider extends GeoServerDataProvider<APIConformance> {

        static final Property<APIConformance> LEVEL = new BeanProperty<>("level");
        static final Property<APIConformance> TYPE = new BeanProperty<>("type");

        private final ConformanceInfo<?> conformanceInfo;
        private final Component parent;

        public ConformanceDataProvider(ConformanceInfo<?> conformanceInfo, Component parent) {
            this.conformanceInfo = conformanceInfo;
            this.parent = parent;
        }

        @Override
        protected List<Property<APIConformance>> getProperties() {
            Property<APIConformance> enabled = new AbstractProperty<>(ENABLED) {
                @Override
                public Object getPropertyValue(APIConformance item) {
                    return new IModel<Boolean>() {

                        @Override
                        public Boolean getObject() {
                            return conformanceInfo.isEnabled(item);
                        }

                        @Override
                        public void setObject(Boolean object) {
                            conformanceInfo.setEnabled(item, object);
                        }
                    };
                }
            };
            Property<APIConformance> name = new AbstractProperty<>(NAME) {
                @Override
                public Object getPropertyValue(APIConformance item) {
                    return new ParamResourceModel(conformanceInfo.getId() + "." + item.getProperty(), parent)
                            .getString();
                }

                @Override
                public IModel<?> getModel(IModel<APIConformance> itemModel) {
                    return super.getModel(itemModel);
                }
            };
            return List.of(enabled, name, LEVEL, TYPE);
        }

        @Override
        protected List<APIConformance> getItems() {
            return conformanceInfo.configurableConformances();
        }
    }
}
