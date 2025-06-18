package org.dbpedia.moss.utils;

import java.util.HashMap;

/** 
 * Helper class for accessing environment variables for MOSS
 */
public class ENV {

    public static final String GSTORE_BASE_URL;
    public static final String STORE_SPARQL_ENDPOINT; // TODO: Remove, when gstore sparql proxy is fixed
    public static final String CONFIG_PATH;
    public static final String LOOKUP_BASE_URL;
    public static final String USER_DATABASE_PATH;
    public static final String AUTH_OIDC_ISSUER;
    public static final String AUTH_OIDC_CLIENT_ID;
    public static final String AUTH_OIDC_CLIENT_SECRET;
    public static final String AUTH_OIDC_DISCOVERY_URL;
    public static final String MOSS_BASE_URL;
    public static final String AUTH_ADMIN_ROLE;
    public static final String AUTH_ADMIN_USERS;
    public static final String MOSS_LOG_LEVEL;



    private static HashMap<String, String> _testVariableMap;
    
    static {

        GSTORE_BASE_URL = getVariable("GSTORE_BASE_URL");
        CONFIG_PATH = getVariable("CONFIG_PATH");
        LOOKUP_BASE_URL = getVariable("LOOKUP_BASE_URL");
        MOSS_BASE_URL = getVariable("MOSS_BASE_URL");
        USER_DATABASE_PATH = getVariable("USER_DATABASE_PATH");
        AUTH_OIDC_ISSUER = getVariable("AUTH_OIDC_ISSUER");
        AUTH_OIDC_CLIENT_ID = getVariable("AUTH_OIDC_CLIENT_ID");
        AUTH_OIDC_CLIENT_SECRET = getVariable("AUTH_OIDC_CLIENT_SECRET");
        AUTH_OIDC_DISCOVERY_URL = getVariable("AUTH_OIDC_DISCOVERY_URL");
        AUTH_ADMIN_ROLE = getVariable("AUTH_ADMIN_ROLE");
        STORE_SPARQL_ENDPOINT = getVariable("STORE_SPARQL_ENDPOINT");
        AUTH_ADMIN_USERS = getVariable("AUTH_ADMIN_USERS");
        MOSS_LOG_LEVEL = getVariable("MOSS_LOG_LEVEL");
    }

    public static void setTestVariable(String key, String value) {
        if (_testVariableMap == null) {
            _testVariableMap = new HashMap<>();
        }
        _testVariableMap.put(key, value);
    }

    private static String getVariable(String name) {
        if (_testVariableMap != null && _testVariableMap.containsKey(name)) {
            return _testVariableMap.get(name);
        }
        return System.getenv(name);
    }

    public static String printAll() {
        StringBuilder sb = new StringBuilder();

        sb.append("GSTORE_BASE_URL: ").append(GSTORE_BASE_URL).append("\n");
        sb.append("CONFIG_PATH: ").append(CONFIG_PATH).append("\n");
        sb.append("LOOKUP_BASE_URL: ").append(LOOKUP_BASE_URL).append("\n");
        sb.append("MOSS_BASE_URL: ").append(MOSS_BASE_URL).append("\n");
        sb.append("USER_DATABASE_PATH: ").append(USER_DATABASE_PATH).append("\n");
        sb.append("AUTH_ADMIN_ROLE: ").append(AUTH_ADMIN_ROLE).append("\n");
        sb.append("AUTH_ADMIN_USERS: ").append(AUTH_ADMIN_USERS).append("\n");
        sb.append("AUTH_OIDC_ISSUER: ").append(AUTH_OIDC_ISSUER).append("\n");
        sb.append("AUTH_OIDC_CLIENT_ID: ").append(AUTH_OIDC_CLIENT_ID).append("\n");
        sb.append("AUTH_OIDC_DISCOVERY_URL: ").append(AUTH_OIDC_DISCOVERY_URL).append("\n");
        sb.append("STORE_SPARQL_ENDPOINT: ").append(STORE_SPARQL_ENDPOINT).append("\n");
        sb.append("MOSS_LOG_LEVEL: ").append(MOSS_LOG_LEVEL).append("\n");
        return sb.toString();
    }
}