
package org.dbpedia.moss;
import org.dbpedia.moss.DatabusMetadataLayerData;


public class DatabusMetadataLayerData {

    private String databusResource;
    private String layerName;
    private String version;
    private String uri;
    private String repo;

    public String getDatabusResource() {
        return databusResource;
    }

    public void setDatabusResource(String databusResource) {
        this.databusResource = databusResource;
    }

    public String getLayerName() {
        return layerName;
    }

    public void setLayerName(String layerName) {
        this.layerName = layerName;
    }

    public String getRepo() {
        return repo;
    }

    public void setRepo(String repo) {
        this.repo = repo;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    private String path;

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public DatabusMetadataLayerData(String uri) {
        this.uri = uri;
    }

    public DatabusMetadataLayerData(String layerName, String version, String databusResource) {
        this.databusResource = databusResource;
        this.layerName = layerName;
        this.version = version;
    }

    public DatabusMetadataLayerData() { }

    public String getDatabusURI() {
        return databusResource;
    }

    public void setDatabusURI(String databusResource) {
        this.databusResource = databusResource;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public boolean isValid() {
        if(uri == null) {
            return false;
        }

        if(layerName == null) {
            return false;
        }

        if(databusResource == null) {
            return false;
        }

        return true;
    }

    

}
