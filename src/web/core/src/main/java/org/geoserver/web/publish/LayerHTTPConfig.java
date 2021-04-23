package org.geoserver.web.publish;

import org.apache.wicket.model.IModel;
import org.geoserver.catalog.LayerInfo;

public class LayerHTTPConfig extends HTTPLayerConfig {
    public LayerHTTPConfig(String id, IModel<LayerInfo> model) {
        super(id, model, "resource.metadata");
    }
}
