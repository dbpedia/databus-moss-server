package org.dbpedia.moss.config;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.naming.ConfigurationException;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.dbpedia.moss.utils.ENV;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

public class MossConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(MossConfiguration.class);

    private String indexerConfigPath;

    public String getIndexerConfigPath() {
        return indexerConfigPath;
    }

    public void setIndexerConfigPath(String indexerPath) {
        this.indexerConfigPath = indexerPath;
    }

    private List<MossLayerConfiguration> layers;

    private List<MossDataLoaderConfig> loaders;

    private List<MossIndexerConfiguration> indexers;
    

    public void setIndexers(List<MossIndexerConfiguration> indexers) {
        this.indexers = indexers;
    }

    // Getters and Setters for indexers, layers, loaders
    public List<MossIndexerConfiguration> getIndexers() {
        return indexers;
    }

    public List<MossLayerConfiguration> getLayers() {
        return layers;
    }

    public void setLayers(List<MossLayerConfiguration> layers) {
        this.layers = layers;
    }

    public List<MossDataLoaderConfig> getLoaders() {
        return loaders;
    }

    public void setLoaders(List<MossDataLoaderConfig> loaders) {
        this.loaders = loaders;
    }
    
    @JsonIgnore
    private File configDir;

    @JsonIgnore
    public File getConfigDir() {
        return configDir;
    }

    private static MossConfiguration fromJson(File file) throws ConfigurationException {
        try {
            // Load the configuration from the YAML file
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
            MossConfiguration config = mapper.readValue(file, MossConfiguration.class);
    
            // Get the directory of the config file for resolving relative paths
            config.configDir = file.getParentFile();

            // Load template content for each MossLayerType
            for (MossLayerConfiguration layer : config.getLayers()) {

                /* 
                if(layer.getIndexers() != null) {
                    for(String indexerId : layer.getIndexers()) {
                        if(config.getIndexer(indexerId) == null) {
                            System.err.println("Layer " + layer.getId() + " references missing indexer " + indexerId + ".");
                        }
                    }
                }*/
                
                
                if (layer.getTemplatePath() != null && !layer.getTemplatePath().isEmpty()) {
                    // Resolve the template file relative to the config file's directory
                    File templateFile = new File(config.configDir, layer.getTemplatePath());
    
                    // Check if the template file exists and is readable
                    if (templateFile.exists() && templateFile.isFile()) {
                        // Read the content of the template file
                        layer.setTemplatePath(templateFile.getAbsolutePath());
                    } else {
                        System.err.println("Template file not found or not readable: " + templateFile.getAbsolutePath());
                    }
                }

                if (layer.getShaclPath() != null && !layer.getShaclPath().isEmpty()) {
                    // Resolve the template file relative to the config file's directory
                    File shaclFile = new File(config.configDir, layer.getShaclPath());
                    // Check if the template file exists and is readable
                    if (shaclFile.exists() && shaclFile.isFile()) {
                        // Read the content of the template file
                        layer.setShaclPath(shaclFile.getAbsolutePath());
                    } else {
                        System.err.println("Template file not found or not readable: " + shaclFile.getAbsolutePath());
                    }
                }
            }
            
            // HashMap<String, MossIndexerConfiguration> indexerMap = new HashMap<>();

            // Load indexer configurations for indexers
            for(MossIndexerConfiguration indexer : config.getIndexers()) {
                indexer.createOrFetchConfigFile(config);
            }

            /*
            // Load template content for each MossLayerType
            for (MossLayerConfiguration layer : config.getLayers()) {
                for(String indexerPath : layer.getIndexers()) {

                    if(!indexerMap.containsKey(indexerPath)) {
                        MossIndexerConfiguration newIndexerConfig = new MossIndexerConfiguration();
                        newIndexerConfig.setConfigPath(indexerPath);
                        indexerMap.put(indexerPath, newIndexerConfig);
                    }

                    MossIndexerConfiguration indexerConfig = indexerMap.get(indexerPath);
                    indexerConfig.addLayer(layer.getName());
                }
            }

            config.indexers = new ArrayList<>(indexerMap.values());
            */

            return config;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public MossLayerConfiguration getLayerByName(String layerName) {
        for (MossLayerConfiguration layer : layers) {
            if(layer.getName().equals(layerName)) {
                return layer;
            }
        }

        return null;
    }

    public MossIndexerConfiguration getIndexerByName(String indexerName) {
        for (MossIndexerConfiguration indexer : indexers) {
            if(indexer.getName().equals(indexerName)) {
                return indexer;
            }
        }

        return null;
    }

    public MossIndexerConfiguration getIndexer(String indexerId) {
        for (MossIndexerConfiguration indexer : indexers) {
            if(indexer.getId().equals(indexerId)) {
                return indexer;
            }
        }

        return null;
    }


    public void validate() throws ConfigurationException {

        logger.info("Validating MOSS configuration...");
    
        // Try to load shacl files:
        for (MossLayerConfiguration layer : layers) {
            

            logger.info("Validating layer \"{}\"...", layer.getId());
            Lang lang = RDFLanguages.contentTypeToLang(layer.getFormatMimeType());
           
            if(lang == null) {
                throw new ConfigurationException("Missing or unknown RDF language in formatMimeType of layer " 
                    + layer.getId() + ": " + layer.getFormatMimeType());
            }
    
            logger.debug("Language: " +  lang.toLongString());

            if(layer.getShaclPath() != null) {
                logger.debug("SHACL file: " + layer.getShaclPath());
                validateSHACL(layer);
            }
    
            if(layer.getTemplatePath() != null){
                logger.debug("Template file: " +  layer.getTemplatePath());
                validateTemplate(layer, lang);
            }
        }
        
        logger.info("Configuration OK!");
    }
    
    
    private void validateSHACL(MossLayerConfiguration layer) throws ConfigurationException {
        Model shaclModel = RDFDataMgr.loadModel(layer.getShaclPath(), Lang.TURTLE);

        if (shaclModel.isEmpty()) {
            throw new ConfigurationException("The specified SHACL file for layer " + layer.getId() + " is empty or invalid.");
        }
    }

    private void validateTemplate(MossLayerConfiguration layer, Lang lang) throws ConfigurationException {
        RDFDataMgr.loadModel(layer.getTemplatePath(), lang);
    }

    public static void initialize(File configFile) throws ConfigurationException {
        MossConfiguration mossConfiguration = MossConfiguration.fromJson(configFile);
        mossConfiguration.validate();

        instance = mossConfiguration;
    }

    private static MossConfiguration instance;

    public static MossConfiguration get() {
        return instance;
    }

    public void save() {
        
        if (configDir == null) {
            logger.error("Configuration directory is not set. Cannot save configuration.");
            return;
        }
        
        File configFile = new File(ENV.CONFIG_PATH);
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
    
        try {
            // Write YAML configuration to file
            mapper.writerWithDefaultPrettyPrinter().writeValue(configFile, this);
            logger.info("Configuration successfully saved to {}", configFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to save configuration: {}", e.getMessage(), e);
        }
    }


    public MossLayerConfiguration getLayer(String layerId) {
        for (MossLayerConfiguration layer : layers) {
            if(layer.getId().equals(layerId)) {
                return layer;
            }
        }

        return null;
    }

    
    public void addOrReplaceIndexer(MossIndexerConfiguration newIndexer) {
        for (int i = 0; i < indexers.size(); i++) {
            if (indexers.get(i).getName().equals(newIndexer.getName())) {
                indexers.set(i, newIndexer); 
                return;
            }
        }
    
        indexers.add(newIndexer);
    }

    public void addOrReplaceLayer(MossLayerConfiguration inputLayer) {
        for (int i = 0; i < layers.size(); i++) {
            if (layers.get(i).getName().equals(inputLayer.getName())) {
                layers.set(i, inputLayer); 
                return;
            }
        }
    
        layers.add(inputLayer);
    }
    

}
