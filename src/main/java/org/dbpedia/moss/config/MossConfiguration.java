package org.dbpedia.moss.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import javax.naming.ConfigurationException;

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
    
    private String templatePath;

    private String contextPath;

    private String modulePath;

    private String templateResourcePlaceholder;

    private List<MossModule> modules;


    public String getTemplateResourcePlaceholder() {
        return templateResourcePlaceholder;
    }

    public void setTemplateResourcePlaceholder(String templateResourcePlaceholder) {
        this.templateResourcePlaceholder = templateResourcePlaceholder;
    }

    public String getTemplatePath() {
        return templatePath;
    }

    public void setTemplatePath(String templatePath) {
        this.templatePath = templatePath;
    }

    public String getIndexerConfigPath() {
        return indexerConfigPath;
    }

    public void setIndexerConfigPath(String indexerPath) {
        this.indexerConfigPath = indexerPath;
    }
    
    public String getContextPath() {
        return contextPath;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    private List<MossOntologyConfiguration> ontologies;


    public List<MossOntologyConfiguration> getOntologies() {
        return ontologies;
    }

    public void setOntologies(List<MossOntologyConfiguration> ontologies) {
        this.ontologies = ontologies;
    }
    
    @JsonIgnore
    private File configDir;

    @JsonIgnore
    public File getConfigDir() {
        return configDir;
    }

    private static MossConfiguration load(File file) throws ConfigurationException {
        try {
            // Load the configuration from the YAML file
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
            MossConfiguration config = mapper.readValue(file, MossConfiguration.class);

            // Get the directory of the config file for resolving relative paths
            config.configDir = file.getParentFile();
            
            File moduleDirectory = new File(config.configDir, config.getModulePath());
            config.modules = loadModules(moduleDirectory);

           
            if(config.getOntologies() == null) {
                config.setOntologies(new ArrayList<>());
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
            return null;
        }
    }

    private static List<MossModule> loadModules(File directory) throws IOException {

        List<MossModule> result = new ArrayList<>();

        if (directory == null || !directory.isDirectory()) {
            return result;
        }

        File[] children = directory.listFiles();
        if (children == null) {
            return result;
        }

        for (File child : children) {
            if (child.isDirectory()) {
                File moduleFile = new File(child, "module.yml");
                if (moduleFile.exists() && moduleFile.isFile()) {
                    // Handle the module.yml file (e.g., load or parse it)
                    ObjectMapper mapper = new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
                    MossModule config = mapper.readValue(moduleFile, MossModule.class);
                    result.add(config);
                }
            }
        }

        return result;
    }



    /*

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
    
           
        }
        
        logger.info("Configuration OK!");
    }
     
    
    private void validateSHACL(MossLayerConfiguration layer) throws ConfigurationException {
        Model shaclModel = RDFDataMgr.loadModel(layer.getShaclPath(), Lang.TURTLE);

        if (shaclModel.isEmpty()) {
            throw new ConfigurationException("The specified SHACL file for layer " + layer.getId() + " is empty or invalid.");
        }
    }*/

   

    public static void initialize(File configFile) throws ConfigurationException, IOException {

        if (!configFile.exists()) {
            File defaultConfig = new File("./config/moss-default.yml");

            if (defaultConfig.exists()) {
                // Copy the default config to the new location
                logger.info("Creating default config at " + configFile.toPath());
                Files.copy(defaultConfig.toPath(), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                throw new ConfigurationException("Default configuration file not found: " + defaultConfig.getAbsolutePath());
            }
        }

        


        MossConfiguration mossConfiguration = MossConfiguration.load(configFile);
        // mossConfiguration.validate();

        /*

        try {
            configDir = configFile.getParentFile();
        } catch (Exception e) {
        }
        ModuleStore store = new ModuleStore(mossConfiguration.getModuleDirectory().toPath());
        
        for(MossModule module : mossConfiguration.getModules()) {
            store.saveModule(module);
        } */

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

    @JsonIgnore
    public File getModuleDirectory() {
        return new File(configDir, getModulePath());
    }


 

    public String getModulePath() {
        return modulePath;
    }

    public void setModulePath(String modulePath) {
        this.modulePath = modulePath;
    }

    public List<MossModule> getModules() {
        return modules;
    }


}
