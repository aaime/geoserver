package org.geoserver.ogcapi;

import org.geoserver.config.ServiceInfo;
import org.springframework.core.MethodParameter;

public class HTMLResponse {

    String templateName;

    String fileName;

    Class<?> baseClass;

    Class<? extends ServiceInfo> serviceClass;

    Object response;

    public HTMLResponse(Object response, HTMLResponseBody html, MethodParameter returnType) {
        this.response = response;
        this.templateName = html.templateName();
        this.fileName = html.fileName();
        this.baseClass = html.baseClass();
        if (baseClass == Object.class) {
            baseClass = returnType.getContainingClass();
        }
        this.serviceClass = getServiceClass(returnType);
    }

    private Class<? extends ServiceInfo> getServiceClass(MethodParameter returnType) {
        APIService apiService =
                APIDispatcher.getApiServiceAnnotation(returnType.getContainingClass());
        if (apiService != null) {
            return apiService.serviceClass();
        }
        throw new RuntimeException("Could not find the APIService annotation in the controller");
    }

    public String getTemplateName() {
        return templateName;
    }

    public String getFileName() {
        return fileName;
    }

    public Class<?> getBaseClass() {
        return baseClass;
    }

    public Class<? extends ServiceInfo> getServiceClass() {
        return serviceClass;
    }

    public Object getResponse() {
        return response;
    }
}
