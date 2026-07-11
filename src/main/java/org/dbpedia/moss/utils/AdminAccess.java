package org.dbpedia.moss.utils;

import java.util.List;

public final class AdminAccess {

    private AdminAccess() {}

    public static String adminRoleKey() {
        if (ENV.AUTH_ADMIN_ROLE != null && !ENV.AUTH_ADMIN_ROLE.isBlank()) {
            return ENV.AUTH_ADMIN_ROLE;
        }
        if (ENV.AUTH_OIDC_CLIENT_ID != null) {
            return ENV.AUTH_OIDC_CLIENT_ID + "/admin";
        }
        return "admin";
    }

    public static boolean hasAdminRole(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        String adminKey = adminRoleKey();
        return roles.stream().anyMatch(role -> role.equalsIgnoreCase(adminKey));
    }
}
