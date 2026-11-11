package com.sma.admin.auth;

import static org.assertj.core.api.Assertions.*;
import java.sql.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import com.sma.admin.support.TestDatabase;

/** Run explicitly, without a live application: uses only the repository's guarded disposable test database. */
class AuthSecurityMigrationIT {
    @Test void upgradesV3WithoutInventingMfaAndAlsoMigratesAnEmptyDatabase() throws Exception {
        TestDatabase.clean();
        migration("3").migrate();
        try(var c=DriverManager.getConnection(TestDatabase.URL,TestDatabase.USERNAME,TestDatabase.PASSWORD);var s=c.createStatement()) {
            s.executeUpdate("INSERT INTO auth_session (id,tenant_id,user_id,portal,token_hash,expires_at,last_seen_at,created_at,created_by,updated_at,updated_by,deleted) VALUES (71,1,42,'admin',REPEAT('a',64),UTC_TIMESTAMP(3)+INTERVAL 1 HOUR,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0)");
        }
        migration("latest").migrate();
        try(var c=DriverManager.getConnection(TestDatabase.URL,TestDatabase.USERNAME,TestDatabase.PASSWORD);var s=c.createStatement()) {
            try(var result=s.executeQuery("SELECT security_version,authentication_method,mfa_verified_at FROM auth_session WHERE id=71")) {
                assertThat(result.next()).isTrue();assertThat(result.getLong(1)).isZero();assertThat(result.getString(2)).isEqualTo("PASSWORD");assertThat(result.getTimestamp(3)).isNull();
            }
            try(var result=s.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('auth_user_security','auth_totp_credential','auth_security_password','auth_challenge','auth_throttle')")) {result.next();assertThat(result.getInt(1)).isEqualTo(5);}
        }
        migration("latest").validate();
        try(var c=DriverManager.getConnection(TestDatabase.URL,TestDatabase.USERNAME,TestDatabase.PASSWORD);var s=c.createStatement()) {
            s.executeUpdate("INSERT INTO auth_totp_credential(id,tenant_id,user_id,portal,enabled,secret_ciphertext,last_accepted_step,enabled_at,pending_secret_ciphertext,pending_challenge_id,pending_expires_at,created_at,created_by,updated_at,updated_by) VALUES(72,1,42,'admin',1,'encrypted-active',1,UTC_TIMESTAMP(3),'encrypted-pending',73,UTC_TIMESTAMP(3)+INTERVAL 10 MINUTE,UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0)");
            s.executeUpdate("INSERT INTO auth_challenge(id,tenant_id,user_id,portal,token_hash,purpose,account_credential_version,security_version,expires_at,created_at,created_by,updated_at,updated_by) VALUES(73,1,42,'admin',REPEAT('b',64),'LOGIN_TOTP',0,0,UTC_TIMESTAMP(3)+INTERVAL 3 MINUTE,UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0)");
            s.executeUpdate("INSERT INTO auth_user_security(id,tenant_id,user_id,portal,security_version,created_at,created_by,updated_at,updated_by) VALUES(74,1,42,'admin',7,UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0)");
            String restore=java.nio.file.Files.readString(java.nio.file.Path.of("../docs/auth-security-restore.sql")).replaceAll("(?m)^--.*$", "");
            for(int retry=0;retry<2;retry++) for(String statement:restore.split(";")) if(!statement.isBlank()) s.execute(statement);
            try(var result=s.executeQuery("SELECT (SELECT COUNT(*) FROM auth_session)+(SELECT COUNT(*) FROM auth_challenge)")){result.next();assertThat(result.getInt(1)).isZero();}
            try(var result=s.executeQuery("SELECT enabled,secret_ciphertext,pending_secret_ciphertext,pending_challenge_id,last_accepted_step>=FLOOR(UNIX_TIMESTAMP()/30) FROM auth_totp_credential WHERE id=72")){
                result.next();assertThat(result.getBoolean(1)).isTrue();assertThat(result.getString(2)).isEqualTo("encrypted-active");assertThat(result.getString(3)).isNull();assertThat(result.getObject(4)).isNull();assertThat(result.getBoolean(5)).isTrue();
            }
            try(var result=s.executeQuery("SELECT security_version FROM auth_user_security WHERE id=74")){result.next();assertThat(result.getLong(1)).isEqualTo(9);}
        }
        TestDatabase.clean();migration("latest").migrate();migration("latest").validate();
    }
    private Flyway migration(String target) {return Flyway.configure().dataSource(TestDatabase.URL,TestDatabase.USERNAME,TestDatabase.PASSWORD).locations("classpath:db/migration/auth").table("flyway_schema_history_auth").target(target).load();}
}
