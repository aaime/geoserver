/* (c) 2015 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web.admin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.wicket.markup.html.form.TextArea;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;

/**
 * Panel listing a map contents
 * 
 * @author Andrea Aime - GeoSolutions
 *
 */
public class PropertiesMapPanel extends Panel {
    private static final long serialVersionUID = 3252916519606097037L;

    public PropertiesMapPanel(String id, Map<?, ?> map) {
        super(id);

        TextArea ta = new TextArea("propertiesTextArea", new Model<String>(buildContents(map)));
        add(ta);
    }

    private String buildContents(Map<?, ?> map) {
        List<Map.Entry<?, ?>> entries = new ArrayList(map.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<?, ?>>() {

            @Override
            public int compare(Map.Entry<?, ?> p1, Map.Entry<?, ?> p2) {
                if (p1 == null) {
                    return p2 == null ? 1 : 0;
                } else if (p2 == null) {
                    return 1;
                }
                Object k1 = p1.getKey();
                Object k2 = p2.getKey();
                if (k1 == null) {
                    return k2 == null ? 1 : 0;
                } else if (k2 == null) {
                    return 1;
                } else {
                    return k1.toString().compareTo(k2.toString());
                }
            }

        });
        return null;
    }

}
