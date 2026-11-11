-- Each actor issues a fresh key for each intent. Reusing it for another target or
-- operation is a conflict, making result lookup possible after a lost response.
ALTER TABLE common_request_execution
    ADD UNIQUE KEY uk_common_request_actor_key (tenant_id,actor_hash,key_hash);
