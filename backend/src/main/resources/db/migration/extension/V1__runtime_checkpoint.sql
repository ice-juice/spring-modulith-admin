-- Platform runtime metadata, not a tenant business table. A database flag never authorizes class loading.
CREATE TABLE extension_runtime_state (
    id VARCHAR(250) NOT NULL PRIMARY KEY,
    revision BIGINT NOT NULL
);
