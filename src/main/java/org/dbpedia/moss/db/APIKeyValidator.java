package org.dbpedia.moss.db;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.mindrot.jbcrypt.BCrypt;

public class APIKeyValidator {

   
    private UserDatabaseManager userDatabaseManager;

    public APIKeyValidator(UserDatabaseManager databaseManager) {
        this.userDatabaseManager = databaseManager;
    }

    /**
     * Creates an API key for the given sub.
     *
     * @param sub The subject for which the API key is to be created.
     * @return The generated API key.
     */
    public static String createAPIKey(String sub) {
        // Encode the sub in Base64
        String encodedSub = Base64.getEncoder().encodeToString(sub.getBytes(StandardCharsets.UTF_8));

        // Generate a unique token
        String uniqueToken = UUID.randomUUID().toString();

        // Create the API key
        return encodedSub + "_" + uniqueToken;
    }

    public UserInfo getUserInfoForAPIKey(String apiKey) {
        try {
            // Split the API key to extract the sub part
            String[] parts = apiKey.split("_");
            if (parts.length != 2) {
                return null; // Invalid API key format
            }

            // Decode the sub from Base64
            String sub = new String(Base64.getDecoder().decode(parts[0]), StandardCharsets.UTF_8);

            // Retrieve the stored hashed API keys for the sub
            List<String> storedApiKeys = userDatabaseManager.getAPIKeysBySub(sub);

            // Compare the provided API key with the stored ones using BCrypt
            for (String hashedApiKey : storedApiKeys) {

                if (BCrypt.checkpw(apiKey, hashedApiKey)) {

                    UserInfo userInfo = userDatabaseManager.getUserInfoBySub(sub); 

                    if(userInfo == null) {
                        userInfo = new UserInfo();
                        userInfo.setSub(sub);
                    }

                    return userInfo;
                }
            }

            return null; // API key is invalid

        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return null; // Invalid Base64 encoding
        } 
    }
}
