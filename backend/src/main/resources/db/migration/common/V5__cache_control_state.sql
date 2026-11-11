-- One committed row provides data revision and runtime policy atomically.
-- Revision zero supports a first statistics reset without evicting version-zero data.
ALTER TABLE common_cache_revision
    DROP CHECK ck_common_cache_revision_positive,
    ADD COLUMN control_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN stats_epoch BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN paused TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN policy_ttl_millis BIGINT NULL,
    ADD COLUMN policy_negative_ttl_millis BIGINT NULL,
    ADD COLUMN policy_max_entries BIGINT NULL,
    ADD COLUMN policy_max_bytes BIGINT NULL,
    ADD COLUMN policy_max_entry_bytes INT NULL,
    ADD CONSTRAINT ck_common_cache_revision_nonnegative CHECK (revision >= 0 AND control_version >= 0 AND stats_epoch >= 0 AND deleted = 0),
    ADD CONSTRAINT ck_common_cache_control_policy CHECK (
        (policy_ttl_millis IS NULL AND policy_negative_ttl_millis IS NULL AND policy_max_entries IS NULL
         AND policy_max_bytes IS NULL AND policy_max_entry_bytes IS NULL)
        OR
        (policy_ttl_millis IS NOT NULL AND policy_negative_ttl_millis IS NOT NULL AND policy_max_entries IS NOT NULL
         AND policy_max_bytes IS NOT NULL AND policy_max_entry_bytes IS NOT NULL
         AND policy_ttl_millis BETWEEN 1 AND 86400000
         AND policy_negative_ttl_millis BETWEEN 0 AND policy_ttl_millis
         AND policy_max_entries > 0 AND policy_max_bytes > 0
         AND policy_max_entry_bytes > 0 AND policy_max_entry_bytes <= policy_max_bytes)
    );
