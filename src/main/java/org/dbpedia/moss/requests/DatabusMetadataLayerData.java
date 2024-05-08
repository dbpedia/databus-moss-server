
package org.dbpedia.moss.requests;

import org.dbpedia.moss.utils.MossUtils;

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

    public String GetDocumentURI(String mossBaseURL) {

        String uriFragments = MossUtils.getMossDocumentUriFragments(databusURI);
        return MossUtils.getMossDocumentUri(mossBaseURL, uriFragments, name, "jsonld");
    }

    public String GetURI(String mossBaseURL) {

        String uriFragments = MossUtils.getMossDocumentUriFragments(databusURI);
        return MossUtils.getMossDocumentUri(mossBaseURL, uriFragments, name, "jsonld") + "#layer";
    }

    public boolean isValid() {
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
