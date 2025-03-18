package org.dbpedia.moss.utils;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

public final class RDFUris {
    private static final Model model = ModelFactory.createDefaultModel();


    public static final String JSONLD_TYPE = "@type";
    public static final String JSONLD_ID = "@id";
    public static final String JSONLD_VALUE = "@value";
    public static final String JSONLD_LANGUAGE = "@language";
    public static final String JSONLD_CONTEXT = "@context";
    public static final String JSONLD_GRAPH = "@graph";

    public static final String MOSS_NAMESPACE = "http://dataid.dbpedia.org/ns/moss#";

    public static final Resource MOSS_DATABUS_METADATA_LAYER_TYPE = model.createResource("http://dataid.dbpedia.org/ns/moss#DatabusMetadataLayerType");
    
    public static final Resource MOSS_DATABUS_METADATA_LAYER_INDEXER = model.createResource("http://dataid.dbpedia.org/ns/moss#DatabusMetadataLayerIndexer");
    
    public static final Resource MOSS_METADATA_LAYER = model.createResource("http://dataid.dbpedia.org/ns/moss#MetadataLayer");
    
    public static final Resource MOSS_METADATA_ENTRY = model.createResource("http://dataid.dbpedia.org/ns/moss#MetadataEntry");

    public static final Property MOSS_NAME = model.createProperty("http://dataid.dbpedia.org/ns/moss#name");
   
    public static final Property MOSS_LAYER = model.createProperty("http://dataid.dbpedia.org/ns/moss#layer");

    public static final Property MOSS_CONFIG_FILE = model.createProperty("http://dataid.dbpedia.org/ns/moss#configFile");
    
    public static final Property MOSS_MIME_TYPE = model.createProperty("http://dataid.dbpedia.org/ns/moss#mimeType");
    
    public static final Property MOSS_INDEXER = model.createProperty("http://dataid.dbpedia.org/ns/moss#indexer");
    
    public static final Property MOSS_LAYER_TARGET_TYPE = model.createProperty("http://dataid.dbpedia.org/ns/moss#mimeType");
    
    public static final Property MOSS_TEMPLATE = model.createProperty("http://dataid.dbpedia.org/ns/moss#template");
    
    public static final Property MOSS_SHAPES_GRAPH = model.createProperty("http://dataid.dbpedia.org/ns/moss#shapesGraph");
    
    public static final Property MOSS_CONTENT = model.createProperty("http://dataid.dbpedia.org/ns/moss#content");
    
    public static final Property DATABUS_VERSION_PROPERTY = model.createProperty("https://dataid.dbpedia.org/databus#version");
    
    public static final Property MOSS_EXTENDS = model.createProperty("http://dataid.dbpedia.org/ns/moss#extends");
    
    public static final Property MOSS_INSTANCE_OF = model.createProperty("http://dataid.dbpedia.org/ns/moss#instanceOf");


    public static final String MOSS_LAYERNAME = "http://dataid.dbpedia.org/ns/moss#layerName";

    public static final String PROV_USED = "http://www.w3.org/ns/prov#used";

    
    
    public static final String DCT_MODIFIED = "http://purl.org/dc/terms/modified";

    public static final String DCT_CREATED = "http://purl.org/dc/terms/created";
    
    public static final String NS_MOSS = "http://dataid.dbpedia.org/ns/moss#";
    
    public static final String NS_DCT = "http://purl.org/dc/terms/";

    public static final String NS_XSD = "http://www.w3.org/2001/XMLSchema#";

    public static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
}