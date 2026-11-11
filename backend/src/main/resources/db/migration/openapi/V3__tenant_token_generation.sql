-- Previous application bearer tokens must be reissued after tenant lifecycle enforcement is enabled.
ALTER TABLE openapi_token ADD COLUMN tenant_security_generation BIGINT NULL;
