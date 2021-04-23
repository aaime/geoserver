package org.geoserver.web.publish;

import org.apache.wicket.model.IModel;
import org.geoserver.catalog.LayerInfo;

public class GroupHTTPConfig extends HTTPLayerConfig {
    public GroupHTTPConfig(String id, IModel<LayerInfo> model) {
        super(id, model, "metadata");
    }
}
