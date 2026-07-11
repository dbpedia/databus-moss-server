package org.dbpedia.moss.db;

import java.util.ArrayList;
import java.util.List;

public class MossRole {

    private String name;
    private String tokenRole;
    private List<String> permissions = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTokenRole() {
        return tokenRole;
    }

    public void setTokenRole(String tokenRole) {
        this.tokenRole = tokenRole;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }
}
