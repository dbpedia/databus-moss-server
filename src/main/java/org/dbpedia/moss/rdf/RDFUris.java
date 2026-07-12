package org.dbpedia.moss.rdf;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

public final class RDFUris {

    private static final Model model = ModelFactory.createDefaultModel();

    public static final Resource MOSS_METADATA_ENTRY = model.createResource("http://dataid.dbpedia.org/ns/moss#MetadataEntry");

    public static final Property MOSS_CONTENT = model.createProperty("http://dataid.dbpedia.org/ns/moss#content");

    public static final Property MOSS_EXTENDS = model.createProperty("http://dataid.dbpedia.org/ns/moss#extends");

    public static final Property MOSS_INSTANCE_OF = model.createProperty("http://dataid.dbpedia.org/ns/moss#instanceOf");

    public static final String NS_MOSS = "http://dataid.dbpedia.org/ns/moss#";

    public static final String NS_DCT = "http://purl.org/dc/terms/";

    public static final String NS_XSD = "http://www.w3.org/2001/XMLSchema#";
}
