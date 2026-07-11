package org.dbpedia.moss.db;

public final class Permissions {

    private Permissions() {}

    public static final String READ_ENTRIES = "read-entries";
    public static final String WRITE_ENTRIES = "write-entries";
    public static final String READ_SETTINGS = "read-settings";
    public static final String WRITE_SETTINGS = "write-settings";
    public static final String READ_USERS = "read-users";
    public static final String WRITE_ROLES = "write-roles";

    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_MAINTAINER = "maintainer";
    public static final String ROLE_GUEST = "guest";

    public static final String[] ALL = {
            READ_ENTRIES, WRITE_ENTRIES, READ_SETTINGS, WRITE_SETTINGS,
            READ_USERS, WRITE_ROLES
    };
}
