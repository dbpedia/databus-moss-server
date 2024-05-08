
package org.dbpedia.moss.requests;

public class DatabusMetadataLayerData {
    
    private String databusURI;
    private String name;
    private String version;

    public DatabusMetadataLayerData() {

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

    public String GetURI() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'GetURI'");
    }
   
}
