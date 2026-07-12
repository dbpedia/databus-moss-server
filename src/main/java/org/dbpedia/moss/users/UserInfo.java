package org.dbpedia.moss.users;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserInfo {

    private String username;
    private String sub;
    private String[] apiKeys;
    private String[] roles;
    private String[] permissions;

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

    public String[] getRoles() {
        return roles;
    }

    public void setRoles(String[] roles) {
        this.roles = roles;
    }

    public String[] getPermissions() {
        return permissions;
    }

    public void setPermissions(String[] permissions) {
        this.permissions = permissions;
    }
}
