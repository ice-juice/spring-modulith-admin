-- Bounded host-only witness metadata, on the existing reviewed membership rows.
-- No file bytes, paths, secrets or tenant data. Old rows receive no storage qualification.
ALTER TABLE common_cache_node_group
    ADD COLUMN storage_manifest_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN storage_anchor_json JSON NULL;
ALTER TABLE common_cache_node_lease
    ADD COLUMN storage_report_json JSON NULL;
