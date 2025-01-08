package org.dbpedia.moss;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.naming.ConfigurationException;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.dbpedia.moss.indexer.DataLoaderConfig;
import org.dbpedia.moss.indexer.LayerIndexerConfiguration;
import org.dbpedia.moss.indexer.MossLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

public class MossConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(MossConfiguration.class);

    private List<MossLayer> layers;

    private List<DataLoaderConfig> loaders;

    private List<LayerIndexerConfiguration> indexers;
    
    
        // Getters and Setters for indexers, layers, loaders
        public List<LayerIndexerConfiguration> getIndexers() {
            return indexers;
        }
    
        public List<MossLayer> getLayers() {
            return layers;
        }
    
        public void setLayers(List<MossLayer> layers) {
            this.layers = layers;
        }
    
        public List<DataLoaderConfig> getLoaders() {
            return loaders;
        }
    
        public void setLoaders(List<DataLoaderConfig> loaders) {
            this.loaders = loaders;
        }
        
        private File configDir;
    
        public static MossConfiguration fromJson(File file) {
            try {
                // Load the configuration from the YAML file
                ObjectMapper mapper = new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
                MossConfiguration config = mapper.readValue(file, MossConfiguration.class);
        
                // Get the directory of the config file for resolving relative paths
                config.configDir = file.getParentFile();
    
            // Load template content for each MossLayerType
            for (MossLayer layer : config.getLayers()) {
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
            
            HashMap<String, LayerIndexerConfiguration> indexerMap = new HashMap<>();

            // Load template content for each MossLayerType
            for (MossLayer layer : config.getLayers()) {
                for(String indexerPath : layer.getIndexers()) {

                    if(!indexerMap.containsKey(indexerPath)) {
                        LayerIndexerConfiguration newIndexerConfig = new LayerIndexerConfiguration();
                        newIndexerConfig.setConfigPath(indexerPath);
                        indexerMap.put(indexerPath, newIndexerConfig);
                    }

                    LayerIndexerConfiguration indexerConfig = indexerMap.get(indexerPath);
                    indexerConfig.addLayer(layer.getName());
                }
            }

            config.indexers = new ArrayList<>(indexerMap.values());


            return config;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public MossLayer getLayerByName(String layerName) {
        for (MossLayer layer : layers) {
            if(layer.getName().equals(layerName)) {
                return layer;
            }
        }

        return null;
    }
    public void validate() throws ConfigurationException {

        logger.info("Validating MOSS configuration...");
    
        // Try to load shacl files:
        for (MossLayer layer : layers) {
            
            
            logger.info("Validating layer \"{}\"...", layer.getName());
            Lang lang = RDFLanguages.contentTypeToLang(layer.getFormatMimeType());
           
            if(lang == null) {
                throw new ConfigurationException("Missing or unknown RDF language in formatMimeType of layer " 
                    + layer.getName() + ": " + layer.getFormatMimeType());
            }
    
            logger.debug("Language: " +  lang.toLongString());
    
            if(layer.getResourceTypes() == null) {
                throw new ConfigurationException("Resource type not specified for layer " + layer.getName() + ".");
            }
    
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
    
    
    private void validateSHACL(MossLayer layer) throws ConfigurationException {
        Model shaclModel = RDFDataMgr.loadModel(layer.getShaclPath(), Lang.TURTLE);

        if (shaclModel.isEmpty()) {
            throw new ConfigurationException("The specified SHACL file for layer " + layer.getName() + " is empty or invalid.");
        }
    }

    private void validateTemplate(MossLayer layer, Lang lang) throws ConfigurationException {
        RDFDataMgr.loadModel(layer.getTemplatePath(), lang);
    }
}
