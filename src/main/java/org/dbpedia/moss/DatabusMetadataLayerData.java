
package org.dbpedia.moss;

import java.io.InputStream;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.RDF;
import org.dbpedia.moss.DatabusMetadataLayerData;
import org.dbpedia.moss.servlets.ValidationException;
import org.dbpedia.moss.utils.MossUtils;
import org.dbpedia.moss.utils.RDFUris;


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

    private static Resource getMetadataLayerURI(Model model) throws Exception {
        Resource metadataLayerType = ResourceFactory.createResource(RDFUris.MOSS_DATABUS_METADATA_LAYER);
        ExtendedIterator<Statement> metadataLayerStatements = model.listStatements(null, RDF.type, metadataLayerType);

        if(!metadataLayerStatements.hasNext()) {
            throw new ValidationException("No Subject of type MetadataLayer found!");
        }

        Statement statement = metadataLayerStatements.next();

        // Remember to close the iterator
        metadataLayerStatements.close();

        return statement.getSubject();
    }

    public static DatabusMetadataLayerData parse(String baseURL, InputStream inputStream) throws Exception {
        String TMP_BASE_URL = "http://example.org/tmp";
        Model tmpModel = ModelFactory.createDefaultModel();
        RDFDataMgr.read(tmpModel, inputStream, TMP_BASE_URL, Lang.JSONLD);

        Resource metadataLayerResource = getMetadataLayerURI(tmpModel);;
        String layerName = null;
        String databusResource = null;

        // Read all triples that have this resource as a subject
        StmtIterator stmtIterator = tmpModel.listStatements(metadataLayerResource, null, (RDFNode) null);

        while (stmtIterator.hasNext()) {
            Statement triple = stmtIterator.next();
            RDFNode object = triple.getObject();
            String predicateURI = triple.getPredicate().getURI();

            // Do something with the triple
            System.out.println("Triple: " + triple);

            // Check the predicate of the triple and set the corresponding field in layerData
            if (predicateURI.equals(RDFUris.MOSS_LAYERNAME)) {
                // layerData.setName(object.toString());
                layerName = object.toString();
                continue;
            }

            if (predicateURI.equals(RDFUris.MOSS_EXTENDS)) {
                // layerData.setDatabusURI(object.toString());
                databusResource = object.toString();
                continue;
            }
        }

        stmtIterator.close();


        if(layerName == null || layerName.length() == 0) {
            throw new ValidationException("Invalid layer name.");
        }

        if(databusResource == null || !MossUtils.isValidResourceURI(databusResource)) {
            throw new ValidationException("Invalid resource URI.");
        }

        /*TODO:
            1. layerName must be a known layer (specified in moss config)
            2. databusResource must be a valid URI
                - optional
                    -> request on databusResource
                        -> check for 200/response.ok (at least response<400)
        */
        String repo = MossUtils.getGStoreRepo(databusResource);
        String path = MossUtils.getGStorePath(databusResource, layerName);

        if(repo == null) {
            throw new ValidationException("Empty repo.");
        }

        if(path == null) {
            throw new ValidationException("Empty path.");
        }

        String extension = ".jsonld";
        if(!path.endsWith(extension)) {
            path += extension;
        }

        String documentURL = MossUtils.createDocumentURI(baseURL, repo, path);
        Model model = ModelFactory.createDefaultModel();
        inputStream.reset();
        RDFDataMgr.read(model, inputStream, documentURL, Lang.JSONLD);
        metadataLayerResource = getMetadataLayerURI(model);
        DatabusMetadataLayerData layerData = new DatabusMetadataLayerData();

        layerData.setDatabusResource(databusResource);
        layerData.setLayerName(layerName);
        layerData.setPath(path);
        layerData.setRepo(repo);
        layerData.setUri(metadataLayerResource.toString());

        return layerData;
    }

}
