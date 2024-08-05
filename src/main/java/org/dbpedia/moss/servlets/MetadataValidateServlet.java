package org.dbpedia.moss.servlets;

import java.io.InputStream;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.RDF;
import org.dbpedia.moss.DatabusMetadataLayerData;
import org.dbpedia.moss.utils.MossUtils;
import org.dbpedia.moss.utils.RDFUris;



public class MetadataValidateServlet {

    private static final String TMP_BASE_URL = "http://example.org/tmp";

    public DatabusMetadataLayerData validateInputStream(InputStream inputStream) throws Exception {
        Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model, inputStream, TMP_BASE_URL, Lang.JSONLD);

        Resource metadataLayerType = ResourceFactory.createResource(RDFUris.MOSS_DATABUS_METADATA_LAYER);
        ExtendedIterator<Statement> metadataLayerStatements = model.listStatements(null, RDF.type, metadataLayerType);

        if(!metadataLayerStatements.hasNext()) {
            throw new ValidationException("Empty Metadata Layer Statements.");
        }

        Statement statement = metadataLayerStatements.next();

        // Remember to close the iterator
        metadataLayerStatements.close();

        Resource resource = statement.getSubject();
        DatabusMetadataLayerData layerData = new DatabusMetadataLayerData(resource.toString());

        // Do something with the resource
        System.out.println("Resource of type " + metadataLayerType + ": " + resource);

        // Read all triples that have this resource as a subject
        StmtIterator stmtIterator = model.listStatements(resource, null, (RDFNode) null);

        while (stmtIterator.hasNext()) {
            Statement triple = stmtIterator.next();
            RDFNode object = triple.getObject();
            String predicateURI = triple.getPredicate().getURI();

            // Do something with the triple
            System.out.println("Triple: " + triple);

            // Check the predicate of the triple and set the corresponding field in layerData
            if (predicateURI.equals(RDFUris.MOSS_LAYERNAME)) {
                layerData.setName(object.toString());
                continue;
            }

            if (predicateURI.equals(RDFUris.MOSS_EXTENDS)) {
                layerData.setDatabusURI(object.toString());
                continue;
            }
        }

        stmtIterator.close();

        return layerData;
    }

    public Tuple2<String, String> validateLayerData(DatabusMetadataLayerData layerData) throws Exception {
        if(layerData.isValid()) {

            String layerName = layerData.getName();
            String resourceUri = layerData.getDatabusURI();

            if(layerName == null || layerName.length() == 0) {
                throw new ValidationException("Invalid layer name.");
            }

            if(resourceUri == null || !MossUtils.isValidResourceURI(resourceUri)) {
                throw new ValidationException("Invalid resource URI.");
            }

            String repo = MossUtils.getGStoreRepo(resourceUri);
            String path = MossUtils.getGStorePath(resourceUri, layerName);

            System.out.println("REQ Repo: " + repo);
            System.out.println("REQ Path: " + path);

            if(repo == null) {
                throw new ValidationException("Empty repo.");
            }

            if(path == null) {
                throw new ValidationException("Empty path.");
            }

            return new Tuple2<String, String>(repo, path);

        } else {
            throw new Exception("Invalid layer data: " + layerData.toString());
        }

    }

    public class ValidationException extends Exception {
        public ValidationException () { }

        public ValidationException (String message) {
            super (message);
        }

        public ValidationException (Throwable cause) {
            super (cause);
        }

        public ValidationException (String message, Throwable cause) {
            super (message, cause);
        }
    }

    public class Tuple2<A, B> {
        private final A repo;
        private final B path;

        public Tuple2(A repo, B path) {
            this.repo = repo;
            this.path = path;
        }

        public A getRepo() {
            return repo;
        }

        public B getPath() {
            return path;
        }

        @Override
        public String toString() {
            return "(" + repo + ", " + path + ")";
        }
    }

}
