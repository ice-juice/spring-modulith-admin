package com.sma.release;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.*;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.sma.extension.protocol.ProtocolJson;
import com.sma.release.auth.*;
import com.sma.release.crypto.*;
import com.sma.release.store.ReleaseDatabase;

class BootstrapRecoveryTests {
    @TempDir Path dir;
    @Test void committedBootstrapCanReexportItsEncryptedReceiptWithoutCreatingAnotherAdmin()throws Exception {
        privateDirectory(dir);Path input=dir.resolve("bootstrap.json"),output=dir.resolve("result.json");
        var data=new LocalBootstrap.Bootstrap("operator","InitialLoginPassword!391","InitialOperationPassword!728",
                List.of(new LocalBootstrap.MachineInput("node",MachineService.Kind.NODE,Set.of("production"),"n".repeat(43))),output.toString());
        byte[] original=ProtocolJson.write(data);Files.write(input,original);
        var properties=new ReleaseProperties("security","host",dir.resolve("store"),java.util.Base64.getEncoder().encodeToString(new byte[32]),"https://security.example",input.toString());
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        var instant=new java.util.concurrent.atomic.AtomicReference<>(java.time.Instant.parse("2026-11-11T00:00:00Z"));
        Clock clock=new Clock(){public java.time.Instant instant(){return instant.get();}public java.time.ZoneId getZone(){return java.time.ZoneOffset.UTC;}public Clock withZone(java.time.ZoneId zone){return this;}};
        var database=new ReleaseDatabase(new JdbcTemplate(ds),new TransactionTemplate(new DataSourceTransactionManager(ds)),clock);
        var vault=new SecretVault(properties);var credentials=new CredentialService(vault);var identity=new IdentityService(database,credentials);
        var bootstrap=new LocalBootstrap(properties,database,identity,new MachineService(credentials),new SigningService(vault,properties),vault);
        bootstrap.run(new DefaultApplicationArguments());byte[] result=Files.readAllBytes(output);assertThat(Files.exists(input)).isFalse();
        Files.delete(output);Files.write(input,original);bootstrap.run(new DefaultApplicationArguments());
        assertThat(Files.readAllBytes(output)).isEqualTo(result);
        int count=database.transaction(tx -> tx.list("user",SecurityRecords.User.class).size());assertThat(count).isEqualTo(1);
        var raw=new JdbcTemplate(ds).queryForObject("SELECT body FROM release_document WHERE kind='bootstrap-receipt' AND id='initial'",String.class);
        var ticket=ProtocolJson.read(result,LocalBootstrap.Result.class);
        assertThat(raw).doesNotContain(ticket.account().enrollmentToken()).doesNotContain(data.loginPassword()).doesNotContain(data.operationPassword());
        instant.updateAndGet(now -> now.plusSeconds(901));Files.write(input,original);
        assertThatThrownBy(() -> bootstrap.run(new DefaultApplicationArguments())).hasMessage("bootstrap-expired-result-must-be-removed");
        Files.delete(output);bootstrap.run(new DefaultApplicationArguments());
        var renewed=ProtocolJson.read(Files.readAllBytes(output),LocalBootstrap.Result.class);
        assertThat(renewed.account().userId()).isEqualTo(ticket.account().userId());assertThat(renewed.account().enrollmentToken()).isNotEqualTo(ticket.account().enrollmentToken());
        assertThatThrownBy(() -> identity.beginEnrollment(data.username(),data.loginPassword(),ticket.account().enrollmentToken(),"127.0.0.1")).hasMessage("authentication-failed");
        assertThat(identity.beginEnrollment(data.username(),data.loginPassword(),renewed.account().enrollmentToken(),"127.0.0.1").secret()).isNotBlank();
    }
    private static void privateDirectory(Path path)throws Exception {
        if(Files.getFileStore(path).supportsFileAttributeView("posix")) Files.setPosixFilePermissions(path,PosixFilePermissions.fromString("rwx------"));
        else {
            var acl=Files.getFileAttributeView(path,AclFileAttributeView.class);
            acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(acl.getOwner())
                    .setFlags(AclEntryFlag.DIRECTORY_INHERIT,AclEntryFlag.FILE_INHERIT).setPermissions(java.util.EnumSet.allOf(AclEntryPermission.class)).build()));
        }
    }
}
