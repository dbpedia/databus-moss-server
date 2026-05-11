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

    private String facetPath;

    private String terminologyPath;

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

    private List<MossTerminology> terminologies;

    public List<MossTerminology> getTerminologies() {
        return terminologies;
    }

    public void setOntologies(List<MossTerminology> ontologies) {
        this.terminologies = ontologies;
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
            config.modules = loadModules(config.getModuleDirectory());
            config.terminologies = loadTerminologies(config.getTerminologyDirectory());

            return config;
        } catch (IOException e) {
            logger.error(e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private static List<MossTerminology> loadTerminologies(File directory) throws IOException {

        List<MossTerminology> result = new ArrayList<>();

        if (directory == null || !directory.isDirectory()) {
            return result;
        }

        File[] children = directory.listFiles();
        if (children == null) {
            return result;
        }

        for (File child : children) {
            if (child.isDirectory()) {
                File terminologyFile = new File(child, "terminology.yml");
                if (terminologyFile.exists() && terminologyFile.isFile()) {
                    // Handle the module.yml file (e.g., load or parse it)
                    ObjectMapper mapper = new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
                    MossTerminology terminology = mapper.readValue(terminologyFile, MossTerminology.class);
                    result.add(terminology);
                }
            }
        }

        return result;
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

        if (mossConfiguration.facetPath == null) {
            mossConfiguration.facetPath = "facets";
        }

        if (mossConfiguration.terminologyPath == null) {
            mossConfiguration.terminologyPath = "terminologies";
        }

        if (mossConfiguration.modulePath == null) {
            mossConfiguration.modulePath = "modules";
        }

        instance = mossConfiguration;

        // Ensure directories exist
        createDirectoryIfMissing(mossConfiguration.getFacetDirectory());
        createDirectoryIfMissing(mossConfiguration.getTerminologyDirectory());
        createDirectoryIfMissing(mossConfiguration.getModuleDirectory());
    }

    private static void createDirectoryIfMissing(File dir) throws IOException {
        if (!dir.exists()) {
            Files.createDirectories(dir.toPath());
            logger.info("Created missing directory: " + dir.getAbsolutePath());
        }
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

    @JsonIgnore
    public File getTerminologyDirectory() {
        return new File(configDir, getTerminologyPath());
    }

    @JsonIgnore
    public File getFacetDirectory() {
        return new File(configDir, getFacetPath());
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

    public String getTerminologyPath() {
        return terminologyPath;
    }

    public String getFacetPath() {
        return facetPath;
    }

    public void setTerminologyPath(String terminologyPath) {
        this.terminologyPath = terminologyPath;
    }

}
