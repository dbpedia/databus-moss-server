package org.dbpedia.moss.indexer;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.dbpedia.moss.utils.RDFUris;
import org.dbpedia.moss.utils.RDFUtils;
import org.slf4j.Logger;

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
   
    private String URI;

    private String databusResourceURI;

    private String layerURI;

    private String contentGraphURI;

    private String createdTime;

    private String modifiedTime;

    public String getDatabusResourceURI() {
        return databusResourceURI;
    }

    public void setDatabusResourceURI(String databusResource) {
        this.databusResourceURI = databusResource;
    }

    public String getLayerURI() {
        return layerURI;
    }

    public void setLayerURI(String layerName) {
        this.layerURI = layerName;
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

    public String getURI() {
        return URI;
    }

    public void setURI(String uri) {
        this.URI = uri;
    }

    public String getContentGraphURI() {
        return contentGraphURI;
    }

    public void setContentGraphURI(String contentGraphURI) {
        this.contentGraphURI = contentGraphURI;
    }


    public void setLastModifiedBy(String username) {
        // TODO... 
    }

    public Model toModel() {

        Model model = ModelFactory.createDefaultModel();
        
        // Create the resource for the MossLayerHeader using its ID
        Resource layerResource = model.createResource(URI);
        
        layerResource.addProperty(RDF.type, RDFUris.MOSS_METADATA_ENTRY);
        // layerResource.addProperty(RDFUris.MOSS_LAYER, );
        layerResource.addProperty(RDFUris.MOSS_INSTANCE_OF, model.createResource(layerURI));
        layerResource.addProperty(RDFUris.MOSS_EXTENDS, model.createResource(databusResourceURI));
        layerResource.addProperty(DCTerms.created,  model.createTypedLiteral(createdTime, XSDDatatype.XSDdateTime));
        layerResource.addProperty(DCTerms.modified, model.createTypedLiteral(modifiedTime, XSDDatatype.XSDdateTime));
        layerResource.addProperty(RDFUris.MOSS_CONTENT, model.createResource(contentGraphURI));

        model.setNsPrefix("moss", RDFUris.NS_MOSS);
        model.setNsPrefix("dct", RDFUris.NS_DCT);
        model.setNsPrefix("xsd", RDFUris.NS_XSD);
        return model;
    }

    public static MossLayerHeader fromModel(String resourceURI, Model model, Logger logger) {

        MossLayerHeader header = new MossLayerHeader();
        header.setURI(resourceURI);

        String currentTime = ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT);

        if(model == null) {
            // Set creation time to the current time and return!
            logger.info("Creating new header for " + resourceURI);
            header.setCreatedTime(currentTime);
            return header;
        }

        Resource resource = model.getResource(resourceURI);
        header.setCreatedTime(RDFUtils.getPropertyValue(model, resource, DCTerms.created, currentTime));
        // TODO: parse other properties

        return header;
    }

}
