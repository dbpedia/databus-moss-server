package org.dbpedia.moss.utils;

import java.util.HashMap;

/** 
 * Helper class for accessing environment variables for MOSS
 */
public class ENV {

    public static final String GSTORE_BASE_URL;
    public static final String CONFIG_PATH;
    public static final String LOOKUP_BASE_URL;
    public static final String USER_DATABASE_PATH;
    public static final String AUTH_OIDC_ISSUER;
    public static final String AUTH_OIDC_CLIENT_ID;
    public static final String AUTH_OIDC_CLIENT_SECRET;
    public static final String MOSS_BASE_URL;
    public static final String ADMIN_ROLE;

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
        ADMIN_ROLE = getVariable("ADMIN_ROLE");
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
        sb.append("ADMIN_ROLE: ").append(ADMIN_ROLE).append("\n");
        sb.append("AUTH_OIDC_ISSUER: ").append(AUTH_OIDC_ISSUER).append("\n");
        sb.append("AUTH_OIDC_CLIENT_ID: ").append(AUTH_OIDC_CLIENT_ID).append("\n");
        return sb.toString();
    }
}