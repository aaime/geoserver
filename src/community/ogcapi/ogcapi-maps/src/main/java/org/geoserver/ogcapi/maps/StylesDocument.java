/* (c) 2019 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.ogcapi.maps;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.PublishedInfo;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.ogcapi.APIRequestInfo;
import org.geoserver.ogcapi.AbstractDocument;
import org.geoserver.ogcapi.Link;
import org.geoserver.ogcapi.StyleDocument;
import org.geoserver.ows.URLMangler;
import org.geoserver.ows.util.ResponseUtils;
import org.geoserver.wms.WebMap;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** Contains the list of styles for a given collection */
@JsonPropertyOrder({"styles", "links"})
public class StylesDocument extends AbstractDocument {
    private static final String REL_MAP = "map";
    private final PublishedInfo published;

    public StylesDocument(PublishedInfo published) {
        this.published = published;

        addSelfLinks(
                "ogc/maps/collections/"
                        + ResponseUtils.urlEncode(published.prefixedName())
                        + "/styles");
    }

    public List<StyleDocument> getStyles() {
        return getStyleInfos().stream().map(this::toDocument).collect(Collectors.toList());
    }

    private StyleDocument toDocument(StyleInfo s) {
        StyleDocument result;
        if (s != null) result = new StyleDocument(s);
        else
            // layer group case
            result =
                    new StyleDocument(
                            StyleDocument.DEFAULT_STYLE_NAME,
                            "Default style for " + published.prefixedName());

        // links to map producers
        Collection<MediaType> formats =
                APIRequestInfo.get().getProducibleMediaTypes(WebMap.class, true);
        String baseUrl = APIRequestInfo.get().getBaseURL();
        String collectionId = ResponseUtils.urlEncode(published.prefixedName());
        String styleId = ResponseUtils.urlEncode(s.prefixedName());
        for (MediaType format : formats) {
            String apiUrl =
                    ResponseUtils.buildURL(
                            baseUrl,
                            "ogc/maps/collections/" + collectionId + "/styles/" + styleId + "/map",
                            Collections.singletonMap("f", format.toString()),
                            URLMangler.URLType.SERVICE);
            addLink(
                    new Link(
                            apiUrl,
                            REL_MAP,
                            format.toString(),
                            "Map for " + published.prefixedName() + " and style " + s.prefixedName() + " as " + format.toString(),
                            "items"));
        }
        
        return result;
    }

    private List<StyleInfo> getStyleInfos() {
        List<StyleInfo> result = new ArrayList<>();
        if (published instanceof LayerInfo) {
            LayerInfo layer = (LayerInfo) this.published;
            result.addAll(layer.getStyles());
            StyleInfo defaultStyle = layer.getDefaultStyle();
            if (!result.contains(defaultStyle)) result.add(defaultStyle);
        } else if (published instanceof LayerGroupInfo) {
            // layer groups do not have a named style right now
            result.add(null);
        } else {
            throw new RuntimeException("Cannot extract styles from " + published);
        }
        return result;
    }

    @JsonIgnore
    public PublishedInfo getPublished() {
        return this.published;
    }
}