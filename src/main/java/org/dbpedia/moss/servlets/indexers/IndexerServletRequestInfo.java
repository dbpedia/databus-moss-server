package org.dbpedia.moss.servlets.indexers;


import jakarta.servlet.http.HttpServletRequest;


public class IndexerServletRequestInfo {
    
	private static final String CONFIG_YML = "config.yml";

    private final String indexerName;
    private final boolean isConfigRequested;
    
    private IndexerServletRequestInfo(String indexerName, boolean isConfigRequested) {
        this.indexerName = indexerName;
        this.isConfigRequested = isConfigRequested;
    }

    public String getIndexerName() {
        return indexerName;
    }

    public boolean isConfigRequested() {
        return isConfigRequested;
    }

    public static IndexerServletRequestInfo fromRequest(HttpServletRequest req) {
        String pathInfo = req.getPathInfo();
        
        if (pathInfo == null || pathInfo.isEmpty()) {
            return null;
        }

        String[] pathParts = pathInfo.split("/");
        String indexerName;
        boolean isConfigRequested = false;

        if (pathParts.length == 2) { 
            indexerName = pathParts[1]; 
        } else if (pathParts.length == 3) { 
            indexerName = pathParts[1]; 
            if (CONFIG_YML.equals(pathParts[2])) {
                isConfigRequested = true;
            } else {
                return null;
            }
        } else {
            return null;
        }

        return new IndexerServletRequestInfo(indexerName, isConfigRequested);
    }
}
