package org.geoserver.wfs3;

import java.util.Collections;
import java.util.List;
import javax.servlet.Filter;
import javax.xml.namespace.QName;
import org.geoserver.test.GeoServerSystemTestSupport;

public class WFS3TestSupport extends GeoServerSystemTestSupport {

    @Override
    protected List<Filter> getFilters() {
        return Collections.singletonList(new WFS3Filter(getCatalog()));
    }

    protected String getEncodedName(QName qName) {
        if (qName.getPrefix() != null) {
            return qName.getPrefix() + "__" + qName.getLocalPart();
        } else {
            return qName.getLocalPart();
        }
    }
}
