/* (c) 2015 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.ogc;

import static org.junit.Assert.*;

import java.util.Map;

import org.custommonkey.xmlunit.XMLUnit;
import org.custommonkey.xmlunit.XpathEngine;
import org.custommonkey.xmlunit.exceptions.XpathException;
import org.geoserver.ows.util.KvpUtils;
import org.geoserver.wms.WMSTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;

public class ParameterForwarderWMSTest extends WMSTestSupport {

    @Override
    protected void registerNamespaces(Map<String, String> namespaces) {
        namespaces.put("wms", "http://www.opengis.net/wms");
        namespaces.put("ows", "http://www.opengis.net/ows");
        namespaces.put("xlink", "http://www.w3.org/1999/xlink");
    }
    
    @Before
    public void resetParamForwarder() {
        ParameterForwarder.FW_PARAMETERS.clear();
        ParameterForwarder.FW_PARAMETERS.add(ParameterForwarder.DEFAULT_FW_PARAMETERS);
    }

    @Test 
    public void testPlainCapabilities() throws Exception {
        Document dom = dom(get("wms?request=getCapabilities&version=1.3.0"), false);
        
        // print(dom);
        Map<String, Object> params = getBacklinksParams(dom);
        assertNull(params.get("CQL_FILTER"));
    }
    
    @Test 
    public void testMixedParams() throws Exception {
        Document dom = dom(get("wms?request=getCapabilities&version=1.3.0&CQL_FILTER=foo='bar'&abcd=10"), false);
        
        // print(dom);
        Map<String, Object> params = getBacklinksParams(dom);
        assertEquals("foo='bar'", params.get("CQL_FILTER"));
    }
    
    @Test 
    public void testCaseInsensitive() throws Exception {
        Document dom = dom(get("wms?request=getCapabilities&version=1.3.0&CqL_fIlTeR=foo='bar'&abcd=10"), false);
        
        // print(dom);
        Map<String, Object> params = getBacklinksParams(dom);
        assertEquals("foo='bar'", params.get("CQL_FILTER"));
    }
    

    private Map<String, Object> getBacklinksParams(Document dom) throws XpathException {
        XpathEngine xpath = XMLUnit.newXpathEngine();
        String backlink = xpath.evaluate("//wms:Request/wms:GetMap/wms:DCPType/wms:HTTP/wms:Get/wms:OnlineResource/@xlink:href", dom);
        return KvpUtils.parseQueryString(backlink);
    }
    
}
