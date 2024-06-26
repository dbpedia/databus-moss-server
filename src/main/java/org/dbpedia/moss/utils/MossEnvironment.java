package org.dbpedia.moss.utils;

/** 
 * Helper class for accessing environment variables for MOSS
 */
public class MossEnvironment {

    private String gstoreBaseURL;
    private String configPath;
    private String lookupBaseURL;
    private String mossBaseURL;
    private String userDatabasePath;

    /**
	 * Loads the XML Configuration from file
	 * @param path The path of the file
	 * @return True if the configuration has been loaded correctly, false otherwise
	 * @throws Exception 
	 */
	public static MossEnvironment Get() {

        MossEnvironment configuration = new MossEnvironment();
        
        // Read the value of the environment variable
        configuration.gstoreBaseURL = System.getenv("GSTORE_BASE_URL");
        configuration.configPath = System.getenv("CONFIG_PATH");
        configuration.lookupBaseURL = System.getenv("LOOKUP_BASE_URL");
        configuration.mossBaseURL = System.getenv("MOSS_BASE_URL");
        configuration.userDatabasePath = System.getenv("USER_DATABASE_PATH");

		return configuration;
	}

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("GSTORE_BASE_URL: " + gstoreBaseURL + "\n");
        sb.append("CONFIG_PATH: " + configPath + "\n");
        sb.append("LOOKUP_BASE_URL: " + lookupBaseURL + "\n");
        sb.append("MOSS_BASE_URL: " + mossBaseURL + "\n");
        sb.append("USER_DATABASE_PATH: " + userDatabasePath + "\n");
        return sb.toString();
    }

    public String getMossBaseUrl() {
        return mossBaseURL;
    }

    public String GetConfigPath() {
        return configPath;
    }

    public String GetLookupBaseURL() {
        return lookupBaseURL;
    }

    public String getGstoreBaseURL() {
        return gstoreBaseURL;
    }

    public String getUserDatabasePath() {
        return userDatabasePath;
    }
}
