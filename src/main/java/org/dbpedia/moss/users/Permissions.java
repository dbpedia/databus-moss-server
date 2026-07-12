package org.dbpedia.moss.users;

public final class Permissions {

    private Permissions() {}

    public static final String READ_METADATA = "read-metadata";
    public static final String WRITE_ENTRIES = "write-entries";
    public static final String WRITE_MODULES = "write-modules";
    public static final String WRITE_TERMINOLOGIES = "write-terminologies";
    public static final String WRITE_FACETS = "write-facets";
    public static final String READ_USERS = "read-users";
    public static final String WRITE_ROLES = "write-roles";

    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_MAINTAINER = "maintainer";
    public static final String ROLE_DEFAULT = "default";
    public static final String ROLE_PUBLIC = "public";

    public static final String[] ALL = {
            READ_METADATA, WRITE_ENTRIES, WRITE_MODULES, WRITE_TERMINOLOGIES, WRITE_FACETS,
            READ_USERS, WRITE_ROLES
    };
}
