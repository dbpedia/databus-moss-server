package org.dbpedia.moss.servlets.layers;


import jakarta.servlet.http.HttpServletRequest;


public class LayerServletRequestInfo {
    
	private static final String TEMPLATE_FILE_NAME = "template";

    private final String layerName;

    private final String templateFile;

    public boolean isTemplateRequested() {
        return templateFile != null;
    }

    public String getTemplateFile() {
        return templateFile;
    }

    private LayerServletRequestInfo(String indexerName, String templateFile) {
        this.layerName = indexerName;
        this.templateFile = templateFile;
    }

    public String getLayerName() {
        return layerName;
    }

  

    public static LayerServletRequestInfo fromRequest(HttpServletRequest req) {
        String pathInfo = req.getPathInfo();
        
        if (pathInfo == null || pathInfo.isEmpty()) {
            return null;
        }

        String[] pathParts = pathInfo.split("/");
        String indexerName;
        String templateFile = null;

        if (pathParts.length == 2) { 
            indexerName = pathParts[1]; 
        } else if (pathParts.length == 3) { 
            indexerName = pathParts[1]; 
            if (pathParts[2].startsWith(TEMPLATE_FILE_NAME)) {
                templateFile = pathParts[2];
            } else {
                return null;
            }
        } else {
            return null;
        }

        return new LayerServletRequestInfo(indexerName, templateFile);
    }

    public boolean isResourceRequested() {
        return templateFile == null;        
    }
}
