package com.sma.admin.common.internal.deployment;

import static org.assertj.core.api.Assertions.*;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import com.sma.admin.support.TestDatabase;

/** Standalone guarded destructive test. Never run next to an integration context on this schema. */
class DeploymentEvidenceMigrationIT {
    @Test void upgradesV13WithoutTrustingLegacyMembershipThenReplaysAndBuildsEmptySchema()throws Exception{
        TestDatabase.clean();migration("13").migrate();
        try(var connection=DriverManager.getConnection(TestDatabase.URL,TestDatabase.USERNAME,TestDatabase.PASSWORD);var sql=connection.createStatement()){
            sql.executeUpdate("INSERT INTO common_cache_node_group (id,cluster_hash,manifest_hash,manifest_version,created_at,created_by,updated_at,updated_by,deleted) VALUES (100,REPEAT('a',64),REPEAT('b',64),1,UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0)");
            sql.executeUpdate("INSERT INTO common_cache_node_lease (id,cluster_hash,node_id,process_id,epoch,manifest_hash,lease_until,created_at,created_by,updated_at,updated_by,deleted) VALUES (101,REPEAT('a',64),'a','11111111-1111-1111-1111-111111111111',2,REPEAT('b',64),DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 1 HOUR),UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0)");
        }
        assertThat(migration("14").migrate().migrationsExecuted).isEqualTo(1);migration("14").validate();
        try(var connection=DriverManager.getConnection(TestDatabase.URL,TestDatabase.USERNAME,TestDatabase.PASSWORD);var sql=connection.createStatement()){
            try(var rows=sql.executeQuery("SELECT manifest_version,evidence_nonce,evidence_digest FROM common_cache_node_group WHERE id=100")){
                assertThat(rows.next()).isTrue();assertThat(rows.getLong(1)).isEqualTo(1);assertThat(rows.getString(2)).isNull();assertThat(rows.getString(3)).isNull();
            }
            try(var rows=sql.executeQuery("SELECT epoch,process_id,evidence_state,evidence_until FROM common_cache_node_lease WHERE id=101")){
                assertThat(rows.next()).isTrue();assertThat(rows.getLong(1)).isEqualTo(2);assertThat(rows.getString(2)).isEqualTo("11111111-1111-1111-1111-111111111111");
                assertThat(rows.getString(3)).isNull();assertThat(rows.getTimestamp(4)).isNull();
            }
        }
        assertThat(migration("14").migrate().migrationsExecuted).isZero();
        TestDatabase.clean();assertThat(migration("14").migrate().migrationsExecuted).isEqualTo(14);migration("14").validate();
        TestDatabase.clean();
    }
    private Flyway migration(String target){return Flyway.configure().dataSource(TestDatabase.URL,TestDatabase.USERNAME,TestDatabase.PASSWORD)
            .locations("classpath:db/migration/common").table("flyway_schema_history_common").target(target).load();}
}
