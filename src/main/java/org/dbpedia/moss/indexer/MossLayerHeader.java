package org.dbpedia.moss.indexer;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.DCTerms;
import org.dbpedia.moss.utils.RDFUris;

/*
{
    "@context": "https://raw.githubusercontent.com/dbpedia/databus-moss/dev/devenv/context2.jsonld",
    "@id": "%LAYER_ID%",
    "@type": "DatabusMetadataLayer",
    "layerName": "%LAYER_NAME%",
    "extends": "%DATABUS_RESOURCE%",
    "created": "%CREATED_DATE%",
    "modified": "%UPDATED_DATE%",
    "moss:content" : "%CONTENT_DOCUMENT%" 
 }
 */


public class MossLayerHeader {
   
    private String uri;

    private String headerDocumentURL;
   
    private String databusResource;

    private String layerName;

    private String createdTime;

    private String modifiedTime;

    private String contentDocumentURL;

    public String getHeaderDocumentURL() {
        return headerDocumentURL;
    }

    public void setHeaderDocumentURL(String headerDocumentURL) {
        this.headerDocumentURL = headerDocumentURL;
    }


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

    public String getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(String createdTime) {
        this.createdTime = createdTime;
    }

    public String getModifiedTime() {
        return modifiedTime;
    }

    public void setModifiedTime(String modifiedTime) {
        this.modifiedTime = modifiedTime;
    }

    public String getContentDocumentURL() {
        return contentDocumentURL;
    }

    public void setContentDocumentURL(String contentDocumentURL) {
        this.contentDocumentURL = contentDocumentURL;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }


  

    public void setLastModifiedBy(String username) {
        // TODO... 
    }

   public Model toModel() {
    Model model = ModelFactory.createDefaultModel();
    
    // Create the resource for the MossLayerHeader using its ID
    Resource layerResource = model.createResource(uri);
    
    layerResource.addProperty(model.createProperty(RDFUris.RDF_TYPE), 
    model.createResource(RDFUris.MOSS_DATABUS_METADATA_LAYER));

    // Set the properties on the resource
    layerResource.addProperty(
        model.createProperty(RDFUris.MOSS_LAYERNAME), 
        layerName);
    
    layerResource.addProperty(
        model.createProperty(RDFUris.MOSS_EXTENDS), 
        model.createResource(databusResource));
    
    layerResource.addProperty(
        model.createProperty(RDFUris.MOSS_CONTENT), 
        model.createResource(contentDocumentURL));
    
    layerResource.addProperty(
        DCTerms.created, 
        model.createTypedLiteral(createdTime, XSDDatatype.XSDdateTime));
    
    layerResource.addProperty(
        DCTerms.modified, 
        model.createTypedLiteral(modifiedTime, XSDDatatype.XSDdateTime));

    model.setNsPrefix("moss", RDFUris.NS_MOSS);
    model.setNsPrefix("dct", RDFUris.NS_DCT);
    model.setNsPrefix("xsd", RDFUris.NS_XSD);
    return model;
}

}
