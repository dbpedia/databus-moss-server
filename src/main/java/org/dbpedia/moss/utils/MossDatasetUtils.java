package org.dbpedia.moss.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RiotException;
import org.dbpedia.moss.config.MossModule;
import org.dbpedia.moss.indexer.MossEntryHeader;

public class MossDatasetUtils {

    /**
     * Creates a Dataset for a given resource and module. The dataset contains:
     * - named graph for the entry header - named graph for the entry content
     */
    public static Dataset createEntryDataset(
            MossModule module,
            String resourceUri,
            String username,
            Model contentModel) throws Exception {

        Dataset dataset = DatasetFactory.create();
        String requestBaseURL = ENV.MOSS_BASE_URL;

        // Compute URIs for the named graphs
        String contentGraphUri = MossUtils.getContentGraphUri(
                requestBaseURL,
                resourceUri,
                module.getId(),
                RDFLanguages.contentTypeToLang(module.getLanguage())
        );

        String headerGraphUri = MossUtils.getHeaderGraphUri(
                requestBaseURL,
                resourceUri,
                module.getId()
        );

        // 1. Load or create entry header
        String headerPath = MossUtils.getHeaderStoragePath(resourceUri, module.getId(), Lang.JSONLD);
        GstoreResource headerResource = new GstoreResource(headerPath);
        Model headerModel = headerResource.exists()
                ? headerResource.readModel(Lang.JSONLD)
                : ModelFactory.createDefaultModel();

        String entryURI = MossUtils.getEntryUri(requestBaseURL, resourceUri, module.getId());
        MossEntryHeader header = MossEntryHeader.fromModel(entryURI, headerModel, null);
        header.setModifiedTime(ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT));
        header.setModuleURI(module.getURI());
        header.setDatabusResourceURI(resourceUri);
        header.setContentGraphURI(contentGraphUri);
        header.setLastModifiedBy(username);

        // Add models
        dataset.addNamedModel(headerGraphUri, header.toModel());
        dataset.addNamedModel(contentGraphUri, contentModel);

        return dataset;
    }

    /**
     * Parses RDF string into a Jena Model with given language
     */
    public static Model parseRdfString(String rdfString, Lang lang) throws RiotException, IOException {
        Model model = ModelFactory.createDefaultModel();
        try (InputStream in = new ByteArrayInputStream(rdfString.getBytes())) {
            RDFDataMgr.read(model, in, lang);
        }
        return model;
    }

    /**
     * Loads SHACL shapes from Turtle string into a model
     */
    public static Model parseShaclShapes(String shaclTtl) throws RiotException {
        Model shaclModel = ModelFactory.createDefaultModel();
        RDFParser.create()
                .source(new StringReader(shaclTtl))
                .lang(Lang.TURTLE)
                .parse(shaclModel);
        return shaclModel;
    }
}
