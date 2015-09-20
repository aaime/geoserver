/* (c) 2015 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web.admin;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.commons.beanutils.BeanComparator;
import org.apache.wicket.Component;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.geoserver.web.wicket.GeoServerDataProvider;
import org.geoserver.web.wicket.GeoServerDataProvider.Property;
import org.geoserver.web.wicket.GeoServerTablePanel;

import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils.Collections;

/**
 * Panel listing system properties
 * 
 * @author Andrea Aime - GeoSolutions
 *
 */
public class PropertiesPanel extends Panel {
    
    static class SystemProperty implements Serializable {
        private static final long serialVersionUID = -8471495780980465584L;
        String key;
        String value;
        
        public SystemProperty(String key, String value) {
            super();
            this.key = key;
            this.value = value;
        }

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return "Property [key=" + key + ", value=" + value + "]";
        }
        
        
    }

    public PropertiesPanel(String id) {
        super(id);

        GeoServerTablePanel<SystemProperty> propertiesTable = new GeoServerTablePanel<SystemProperty>(
                "propertiesTable", new PropertiesProvider()) {

                    @Override
                    protected Component getComponentForProperty(String id, IModel itemModel,
                            Property<SystemProperty> property) {
                     // no need to implement, we are ok with strings
                        return null;
                    }

            
        };
        add(propertiesTable);
    }

    static class PropertiesProvider extends GeoServerDataProvider<SystemProperty> {

        @Override
        protected List<org.geoserver.web.wicket.GeoServerDataProvider.Property<SystemProperty>> getProperties() {
            List<GeoServerDataProvider.Property<SystemProperty>> result = new ArrayList<GeoServerDataProvider.Property<SystemProperty>>();
            result.add(new BeanProperty("key", "key"));
            result.add(new BeanProperty("value", "value"));
            return result;
        }

        @Override
        protected List<SystemProperty> getItems() {
            List<SystemProperty> properties = new ArrayList<>();
            for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
                properties.add(new SystemProperty(String.valueOf(entry.getKey()), String.valueOf(entry.getValue())));
            }
            java.util.Collections.sort(properties, new Comparator<SystemProperty>() {

                @Override
                public int compare(SystemProperty p1, SystemProperty p2) {
                    if(p1 == null) {
                        return p2 == null ? 1 : 0;
                    } else if(p2 == null) {
                        return 1;
                    }
                    String k1 = p1.getKey();
                    String k2 = p2.getKey();
                    if(k1 == null) {
                        return k2 == null ? 1 : 0;
                    } else if(k2 == null) {
                        return 1;
                    } else {
                        return k1.compareTo(k2);
                    }
                }
                
            });
            return properties;
        }

    }

}
