-- A stopped switch may be abandoned only before the target binding accepts writes.
ALTER TABLE tenant_storage_switch DROP CHECK ck_tenant_storage_switch;
ALTER TABLE tenant_storage_switch ADD CONSTRAINT ck_tenant_storage_switch CHECK (
    tenant_id>0 AND owner_tenant_id>0 AND source_revision>0 AND target_revision>0
    AND expected_binding_version>0 AND stop_generation>=0
    AND state IN ('PREPARING','COPIED','DRAINING','READY','NEEDS_REVIEW','SWITCHED','ABORTED'));
