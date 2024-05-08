package org.dbpedia.moss.utils;

import java.io.File;

import org.dbpedia.moss.indexer.IndexerManagerConfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class MossConfiguration {

   
    private String gstoreBaseURL;
    private String indexerConfigPath;

    public String getGstoreBaseURL() {
        return gstoreBaseURL;
    }

    public void setGstoreBaseURL(String gstoreBaseURL) {
        this.gstoreBaseURL = gstoreBaseURL;
    }

    public IndexerManagerConfig getIndexerManagerConfig() {
        return IndexerManagerConfig.fromJson(new File(indexerConfigPath));
    }
    
    /**
	 * Loads the XML Configuration from file
	 * @param path The path of the file
	 * @return True if the configuration has been loaded correctly, false otherwise
	 * @throws Exception 
	 */
	public static MossConfiguration Load() {

        MossConfiguration configuration = new MossConfiguration();
        
        // Read the value of the environment variable
        configuration.gstoreBaseURL = System.getenv("GSTORE_BASE_URL");

		return configuration;
	}

}
