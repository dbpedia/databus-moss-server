package org.dbpedia.moss.utils;

import java.util.HashMap;

/** 
 * Helper class for accessing environment variables for MOSS
 */
public class MossEnvironment {

    private String gstoreBaseURL;
    private String configPath;
    private String lookupBaseURL;
    private String mossBaseURL;
    private String userDatabasePath;
    private String authOidcIssuer;
    private String authOidcClientId;
    private String authOidcClientSecret;

    private static HashMap<String, String> _testVariableMap;

    public static void setTestVariable(String key, String value) {
        if(_testVariableMap == null) {
            _testVariableMap = new HashMap<>();
        }

        _testVariableMap.put(key, value);
    }

    /**
	 * Loads the XML Configuration from file
	 * @param path The path of the file
	 * @return True if the configuration has been loaded correctly, false otherwise
	 * @throws Exception 
	 */
	public static MossEnvironment get() {

        MossEnvironment configuration = new MossEnvironment();
        
        // Read the value of the environment variable
        configuration.gstoreBaseURL = getVariable("GSTORE_BASE_URL");
        configuration.configPath = getVariable("CONFIG_PATH");
        configuration.lookupBaseURL = getVariable("LOOKUP_BASE_URL");
        configuration.mossBaseURL = getVariable("MOSS_BASE_URL");
        configuration.userDatabasePath = getVariable("USER_DATABASE_PATH");
        configuration.mossBaseURL = getVariable("MOSS_BASE_URL");
        configuration.authOidcIssuer = getVariable("AUTH_OIDC_ISSUER");
        configuration.authOidcClientId = getVariable("AUTH_OIDC_CLIENT_ID");
        configuration.authOidcClientSecret = getVariable("AUTH_OIDC_CLIENT_SECRET");

		return configuration;
	}

    private static String getVariable(String name) {
        if(_testVariableMap != null && _testVariableMap.containsKey(name)) {
            return _testVariableMap.get(name);
        }

        return  System.getenv(name);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("GSTORE_BASE_URL: " + gstoreBaseURL + "\n");
        sb.append("MOSS_BASE_URL: " + mossBaseURL + "\n");
        sb.append("LOOKUP_BASE_URL: " + lookupBaseURL + "\n");
        sb.append("USER_DATABASE_PATH: " + userDatabasePath + "\n");
        sb.append("CONFIG_PATH: " + configPath + "\n");
        sb.append("AUTH_OIDC_ISSUER: " + authOidcIssuer + "\n");
        sb.append("AUTH_OIDC_CLIENT_ID: " + authOidcClientId + "\n");
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

    public String getAuthOidcIssuer() {
        return authOidcIssuer;
    }

    public String getAuthOidcClientSecret() {
        return authOidcClientSecret;
    }

    public String getAuthOidcClientId() {
        return authOidcClientId;
    }
}
