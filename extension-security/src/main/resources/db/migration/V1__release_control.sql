CREATE TABLE release_document (
    kind VARCHAR(40) NOT NULL,
    id VARCHAR(160) NOT NULL,
    body LONGTEXT NOT NULL,
    PRIMARY KEY (kind, id)
);
CREATE TABLE release_lock (
    id INT PRIMARY KEY,
    revision BIGINT NOT NULL
);
INSERT INTO release_lock (id, revision) VALUES (1, 0);
CREATE TABLE release_audit (
    sequence_id BIGINT PRIMARY KEY,
    occurred_at VARCHAR(40) NOT NULL,
    actor VARCHAR(100) NOT NULL,
    action VARCHAR(80) NOT NULL,
    resource_id VARCHAR(160) NOT NULL,
    outcome VARCHAR(80) NOT NULL,
    detail_hash CHAR(64) NOT NULL,
    previous_hash CHAR(64) NOT NULL,
    entry_hash CHAR(64) NOT NULL
);
