package org.dbpedia.moss.indexer;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.naming.ConfigurationException;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.config.MossTerminology;
import org.dbpedia.moss.servlets.terminologies.DataHandler;
import org.dbpedia.moss.servlets.terminologies.TerminologyStore;
import org.dbpedia.moss.utils.GstoreResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TerminologyLoader {

    final static Logger logger = LoggerFactory.getLogger(TerminologyLoader.class);

    /*
     * Saves moss terminology to gstore and runs the indexer
     */
    public static void load(MossTerminology terminology) throws ConfigurationException {

        var mossConfiguration = MossConfiguration.get();
        var configDir = mossConfiguration.getConfigDir();

        TerminologyStore terminologyStore = new TerminologyStore(mossConfiguration.getTerminologyDirectory().toPath());

        DataHandler dataHandler = new DataHandler(null);
        var dataFileName = dataHandler.getDataFileName(terminology);

        File dataFile = new File(configDir, dataFileName);

        if(!dataFile.exists()) {
            throw new ConfigurationException("Specified config file does not exist: " + dataFile.getAbsolutePath());
        }

        String fileName = dataFile.getName();

        logger.info("Loading ontology data: " + fileName);

        try {
            GstoreResource resource = new GstoreResource(String.format("/ontology/%s", dataFile.getName()));
            Lang lang = RDFLanguages.filenameToLang(dataFile.getAbsolutePath());
            Model model = cleanModel(RDFDataMgr.loadModel(dataFile.getAbsolutePath(), lang));
            resource.writeModel(model, lang);

            logger.info("Ontology graph loaded at: " + resource.getGraphURL());
            Optional<String> indexerConfigurationOpt = terminologyStore.loadSubResource(terminology.getId(), MossTerminology.INDEXER_FILE);

            if(indexerConfigurationOpt.isEmpty()) {
                return;
            }

            var indexerConfiguration = indexerConfigurationOpt.get();
            String formattedConfig = formatIndexerConfigString(indexerConfiguration, resource.getGraphURL());

            IndexGroup group = new IndexGroup(null, new String[] { formattedConfig });
            IndexingTask task = new IndexingTask(null, group);
            task.run();

        } catch (IOException | URISyntaxException e) {
            logger.error("Failed to load annotation data:" + e.getMessage());
        }
    }

    private static String formatIndexerConfigString(String indexerConfiguration, String graphUri) throws IOException {
        return indexerConfiguration.replace("#GRAPH#", String.format("GRAPH <%s>", graphUri));
    }

    public static boolean containsNonUnicode(String str) {
        if (str == null) {
            return false;
        }
        return !Pattern.matches("\\A\\p{ASCII}*\\z", str);
    }

    private static Model cleanModel(Model model) {
        Model cleanedModel = ModelFactory.createDefaultModel();

        StmtIterator iterator = model.listStatements();

        while (iterator.hasNext()) {
            Statement statement = iterator.next();

            if (containsNonUnicode(statement.getSubject().getURI())) {
                continue;
            }

            if (containsNonUnicode(statement.getPredicate().getURI())) {
                continue;
            }

            if (statement.getObject().isResource()) {
                if (containsNonUnicode(statement.getObject().asResource().getURI())) {
                    continue;
                }
            }

            cleanedModel.add(statement);
        }

        return cleanedModel;
    }
}
