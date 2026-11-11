package com.sma.admin.common.persistence;

/** Immutable identity attached to a borrowed JDBC connection by the host, available through JDBC unwrap. */
public interface DataSourceConnectionIdentity {
    DataSourceIdentity dataSourceIdentity();
}
