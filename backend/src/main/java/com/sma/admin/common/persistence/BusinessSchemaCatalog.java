package com.sma.admin.common.persistence;

/** A static business module declares its storage group. The host derives the module and migration path.
 * Put immutable SQL versions at db/business/<module>/V<n>__description.sql, never platform db/migration.
 */
public interface BusinessSchemaCatalog {
    String storageGroup();
}
