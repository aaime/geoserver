/* (c) 2015 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.ogc;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.geoserver.ows.Dispatcher;
import org.geoserver.ows.Request;
import org.geoserver.ows.URLMangler;

public class ParameterForwarder implements URLMangler {

    static final String DEFAULT_FW_PARAMETERS = "CQL_FILTER";

    static final Set<String> FW_PARAMETERS;

    static {
        String property = System.getProperty("FORWARD_PARAMS", DEFAULT_FW_PARAMETERS);
        String[] params = property.split("\\s*,\\s*");
        FW_PARAMETERS = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        FW_PARAMETERS.addAll(Arrays.asList(params));
    }

    @Override
    public void mangleURL(StringBuilder baseURL, StringBuilder path, Map<String, String> kvp,
            URLType type) {
        // are we propagating from a GetCapabilities?
        Request request = Dispatcher.REQUEST.get();
        if (request == null || !"GetCapabilities".equalsIgnoreCase(request.getRequest())) {
            return;
        }

        // propagate params as needed
        Map requestKvp = request.getRawKvp();
        for (Object key : requestKvp.keySet()) {
            if (FW_PARAMETERS.contains(key) && !kvp.containsKey(key)) {
                Object value = requestKvp.get(key);
                if (value instanceof String) {
                    kvp.put((String) key, (String) value);
                } 
            }
        }
    }

}
