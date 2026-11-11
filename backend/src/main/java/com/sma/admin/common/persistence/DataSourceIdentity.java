package com.sma.admin.common.persistence;

/** Deployment/resource-owned identifier. Never contains a JDBC URL, database password or tenant input. */
public record DataSourceIdentity(String resourceId, long revision) {
    public static final DataSourceIdentity UNKNOWN = new DataSourceIdentity("unknown", 0);

    public DataSourceIdentity {
        if (resourceId == null || !resourceId.matches("[a-z][a-z0-9_-]{0,63}") || revision < 0
                || (resourceId.equals("unknown") != (revision == 0))) {
            throw new IllegalArgumentException("Invalid data source identity");
        }
    }
}
