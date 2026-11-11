package com.sma.admin.common.persistence;

/** Allocate an opaque positive Long in this host database. Allocation does not grant object access and is never refunded. */
public interface IdentifierAllocation {
    long nextId();
}
