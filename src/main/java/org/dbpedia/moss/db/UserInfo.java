package org.dbpedia.moss.db;

public class UserInfo {
    
    private String username;

    private String sub;

    private String[] apiKeys;

    private boolean isAdmin;
    
    public String[] getApiKeys() {
        return apiKeys;
    }

    public void setApiKeys(String[] apiKeys) {
        this.apiKeys = apiKeys;
    }

    public String getSub() {
        return sub;
    }
    
    public void setSub(String sub) {
        this.sub = sub;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isIsAdmin() {
        return isAdmin;
    }

    public void setIsAdmin(boolean isAdmin) {
        this.isAdmin = isAdmin;
    }
}
