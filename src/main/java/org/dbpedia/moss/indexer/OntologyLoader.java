package org.dbpedia.moss.indexer;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.dbpedia.moss.config.MossOntologyConfiguration;
import org.dbpedia.moss.utils.GstoreResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OntologyLoader {

    final static Logger logger = LoggerFactory.getLogger(OntologyLoader.class);

    public static void load(MossConfiguration config) throws ConfigurationException {

        File configDir = config.getConfigDir();

        for (MossOntologyConfiguration ontologyConfiguration : config.getOntologies()) {

            File dataFile = new File(configDir, ontologyConfiguration.getDataPath());

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

                File indexerConfigFile = new File(configDir, ontologyConfiguration.getIndexerConfigPath());
                File formattedIndexerConfigFile = formatIndexerConfigFile(indexerConfigFile, resource.getGraphURL());

                IndexGroup group = new IndexGroup(null, new File[] { formattedIndexerConfigFile });

                IndexingTask task = new IndexingTask(null, group);
                task.run();

                formattedIndexerConfigFile.delete();

            } catch (IOException | URISyntaxException e) {
                logger.error("Failed to load annotation data:" + e.getMessage());
            }
        }
    }

    private static File formatIndexerConfigFile(File indexerConfigFile, String graphUri) throws IOException {
        // Read content from the input file
        String content = Files.readString(indexerConfigFile.toPath(), StandardCharsets.UTF_8);

        // Replace occurrences of "#GRAPH#" with graphUri
        content = content.replace("#GRAPH#", String.format("GRAPH <%s>", graphUri));

        // Create a temporary file
        Path tempFile = Files.createTempFile("formatted_indexer_config", ".yml");

        // Write modified content to the temporary file
        Files.writeString(tempFile, content, StandardCharsets.UTF_8);

        return tempFile.toFile();
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