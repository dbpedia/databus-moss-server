package org.dbpedia.moss.app;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.naming.ConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dbpedia.moss.terminologies.MossTerminology;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

public class MossConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(MossConfiguration.class);

    public static final String DEFAULT_FACET_PATH = "facets";

    public static final String DEFAULT_MODULE_PATH = "modules";

    public static final String DEFAULT_TERMINOLOGY_PATH = "terminologies";

    private String modulePath = DEFAULT_MODULE_PATH;

    private String facetPath = DEFAULT_FACET_PATH;

    private String terminologyPath = DEFAULT_TERMINOLOGY_PATH;

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

    /**
     * Resolves {@code CONFIG_PATH}: directory, or legacy path to a YAML file (uses its parent).
     */
    static File resolveConfigRoot(File configPath) throws IOException {
        if (configPath.exists()) {
            if (configPath.isDirectory()) {
                return configPath.getCanonicalFile();
            }
            File parent = configPath.getParentFile();
            return (parent != null ? parent : new File(".")).getCanonicalFile();
        }
        String name = configPath.getName().toLowerCase();
        if (name.endsWith(".yml") || name.endsWith(".yaml")) {
            File parent = configPath.getParentFile();
            return (parent != null ? parent : new File(".")).getCanonicalFile();
        }
        return configPath.getCanonicalFile();
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
                    ObjectMapper mapper = new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
                    MossTerminology terminology = mapper.readValue(terminologyFile, MossTerminology.class);
                    result.add(terminology);
                }
            }
        }

        return result;
    }

    public static void initialize(File configPath) throws ConfigurationException, IOException {

        File configRoot = resolveConfigRoot(configPath);
        if (!configRoot.exists()) {
            Files.createDirectories(configRoot.toPath());
            logger.info("Created config directory: {}", configRoot.getAbsolutePath());
        }
        if (!configRoot.isDirectory()) {
            throw new ConfigurationException("CONFIG_PATH must resolve to a directory: " + configRoot.getAbsolutePath());
        }

        MossConfiguration mossConfiguration = new MossConfiguration();
        mossConfiguration.configDir = configRoot;
        mossConfiguration.terminologies = loadTerminologies(mossConfiguration.getTerminologyDirectory());

        instance = mossConfiguration;

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

    public String getTerminologyPath() {
        return terminologyPath;
    }

    public String getFacetPath() {
        return facetPath;
    }

    public void setFacetPath(String facetPath) {
        this.facetPath = facetPath;
    }

    public void setTerminologyPath(String terminologyPath) {
        this.terminologyPath = terminologyPath;
    }

}
