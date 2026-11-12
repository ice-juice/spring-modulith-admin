package com.sma.admin.common.internal.deployment;
import static org.assertj.core.api.Assertions.*;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import com.sma.admin.support.TestDatabase;

/** Explicit isolated schema upgrade. Does not run alongside a live integration context. */
class SharedStorageMigrationIT {
    @Test void preservesExistingConfigurationEvidenceAndAddsNoImplicitStorageTrust()throws Exception{
        TestDatabase.clean();migration("14").migrate();
        try(var connection=DriverManager.getConnection(TestDatabase.URL,TestDatabase.USERNAME,TestDatabase.PASSWORD);var sql=connection.createStatement()){
            sql.executeUpdate("INSERT INTO common_cache_node_group(id,cluster_hash,manifest_hash,manifest_version,created_at,created_by,updated_at,updated_by,deleted,evidence_manifest_hash,evidence_nonce,evidence_digest) VALUES(100,REPEAT('a',64),REPEAT('b',64),1,UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0,REPEAT('b',64),REPEAT('c',64),REPEAT('d',64))");
            sql.executeUpdate("INSERT INTO common_cache_node_lease(id,cluster_hash,node_id,process_id,epoch,manifest_hash,lease_until,created_at,created_by,updated_at,updated_by,deleted,evidence_state) VALUES(101,REPEAT('a',64),'a','11111111-1111-1111-1111-111111111111',2,REPEAT('b',64),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0,'MATCH')");
        }
        assertThat(migration("15").migrate().migrationsExecuted).isEqualTo(1);migration("15").validate();
        try(var connection=DriverManager.getConnection(TestDatabase.URL,TestDatabase.USERNAME,TestDatabase.PASSWORD);var sql=connection.createStatement()){
            try(var rows=sql.executeQuery("SELECT evidence_digest,storage_manifest_hash,storage_anchor_json FROM common_cache_node_group WHERE id=100")){
                assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("d".repeat(64));assertThat(rows.getString(2)).isNull();assertThat(rows.getString(3)).isNull();
            }
            try(var rows=sql.executeQuery("SELECT epoch,evidence_state,storage_report_json FROM common_cache_node_lease WHERE id=101")){
                assertThat(rows.next()).isTrue();assertThat(rows.getLong(1)).isEqualTo(2);assertThat(rows.getString(2)).isEqualTo("MATCH");assertThat(rows.getString(3)).isNull();
            }
        }
        assertThat(migration("15").migrate().migrationsExecuted).isZero();TestDatabase.clean();
        assertThat(migration("15").migrate().migrationsExecuted).isEqualTo(15);migration("15").validate();TestDatabase.clean();
    }
    private Flyway migration(String target){return Flyway.configure().dataSource(TestDatabase.URL,TestDatabase.USERNAME,TestDatabase.PASSWORD)
            .locations("classpath:db/migration/common").table("flyway_schema_history_common").target(target).load();}
}
