
package org.dbpedia.moss;


public class DatabusMetadataLayerData {

    private String databusURI;
    private String name;
    private String version;
    private String uri;

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public DatabusMetadataLayerData(String uri) {
        this.uri = uri;
    }   

    public DatabusMetadataLayerData(String name, String version, String databusURI) {
        this.databusURI = databusURI;
        this.name = name;
        this.version = version;
    }

    public String getDatabusURI() {
        return databusURI;
    }

    public void setDatabusURI(String databusURI) {
        this.databusURI = databusURI;
    }

    public String getName() {
        return name;
    }

    public void setName(String modType) {
        this.name = modType;
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

        if(name == null) {
            return false;
        }

        if(version == null) {
            return false;
        }
        
        if(databusURI == null) {
            return false;
        }

        return true;
    }
   
}
