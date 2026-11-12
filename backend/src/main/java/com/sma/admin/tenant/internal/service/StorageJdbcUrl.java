package com.sma.admin.tenant.internal.service;

import com.sma.admin.tenant.internal.persistence.StorageResourceEntity;

/** Use only validated platform directory fields, never HTTP supplied JDBC URLs. */
final class StorageJdbcUrl {
    private StorageJdbcUrl() {}

    static String of(StorageResourceEntity resource) {
        return "jdbc:mysql://" + resource.getDbHost() + ":" + resource.getDbPort() + "/"
                + resource.getDatabaseName() + "?sslMode=" + resource.getTlsMode()
                + ("DISABLED".equals(resource.getTlsMode()) ? "&allowPublicKeyRetrieval=true" : "");
    }
}
