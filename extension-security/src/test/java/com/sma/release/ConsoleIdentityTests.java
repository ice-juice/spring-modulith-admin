package com.sma.release;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base32;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import com.sma.extension.protocol.*;
import com.sma.release.auth.*;
import com.sma.release.auth.SecurityRecords.*;
import com.sma.release.crypto.SecretVault;
import com.sma.release.store.ReleaseDatabase;

class ConsoleIdentityTests {
    static final String OP="OperationPassword!123",LOGIN="EmergencyPassword!321",MACHINE="console-machine-secret-01234567890123456789";
    final MutableClock clock=new MutableClock();
    ReleaseDatabase db;IdentityService identity;ConsoleIdentityService consoles;MachineService machines;CredentialService credentials;SecretVault vault;
    NewAccount account;String secret,session;List<String> recovery;
    final ConsoleIdentityService.Subject subject=new ConsoleIdentityService.Subject("1","admin","123456789012345678");
    @BeforeEach void setup()throws Exception {
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        db=new ReleaseDatabase(new JdbcTemplate(ds),new TransactionTemplate(new DataSourceTransactionManager(ds)),clock);
        vault=new SecretVault(new ReleaseProperties("security","host",Path.of("target/console-vault"),Base64.getEncoder().encodeToString(new byte[32]),"https://host.example.test",""));
        credentials=new CredentialService(vault);identity=new IdentityService(db,credentials);machines=new MachineService(credentials);
        consoles=new ConsoleIdentityService(db,machines,credentials,vault,identity);
        account=identity.bootstrap("operator",LOGIN,OP);
        db.transaction(tx->{machines.provision(tx,"host",MachineService.Kind.CONSOLE,Set.of("security"),MACHINE);
            consoles.bindLocal(tx,"operator",new ConsoleIdentityService.Bind("host",subject,account.userId(),0,true));return null;});
        session=exchange("a");
    }
    String exchange(String hash){return consoles.exchange(MACHINE,new ConsoleIdentityService.Exchange(subject,hash.repeat(64))).token();}
    String otp()throws Exception{return String.format("%06d",new TimeBasedOneTimePasswordGenerator().generateOneTimePassword(new SecretKeySpec(new Base32().decode(secret),"HmacSHA1"),clock.instant()));}
    void enroll()throws Exception {
        secret=identity.beginConsoleEnrollment(session,OP,account.enrollmentToken()).secret();
        recovery=identity.completeConsoleEnrollment(session,OP,account.enrollmentToken(),otp());assertThat(recovery).hasSize(10);session=exchange("a");clock.advance(30);
    }
    ActionIntent intent(){return new ActionIntent(ActionIntent.Action.GROUP_CREATE,"production","general",0,"placeholder",0,1,List.of(),Hashes.sha256(new byte[0]));}
    @Test void canReadIdentityWithoutSecondLoginButCannotWriteUntilFactorsAreEnrolled()throws Exception {
        assertThat(identity.principal(session).userId()).isEqualTo(account.userId());
        assertThat(consoles.status(session).enrolled()).isFalse();
        assertThatThrownBy(()->identity.challenge(session,intent())).hasMessage("console-enrollment-required");
        assertThatThrownBy(()->identity.beginConsoleEnrollment(session,"wrong",account.enrollmentToken())).hasMessage("authentication-failed");
        enroll();assertThat(consoles.status(session).enrolled()).isTrue();
        var challenge=identity.challenge(session,intent());
        assertThatThrownBy(()->identity.authorize(session,challenge.id(),"wrong",otp())).hasMessage("authentication-failed");
        var grant=identity.authorize(session,challenge.id(),OP,otp());
        var other=exchange("b");
        assertThatThrownBy(()->db.transaction(tx->identity.consume(tx,other,grant.token(),intent(),"other"))).hasMessage("grant-unavailable");
        db.transaction(tx->identity.consume(tx,session,grant.token(),intent(),"accepted"));
        assertThatThrownBy(()->db.transaction(tx->identity.consume(tx,session,grant.token(),intent(),"replay"))).hasMessage("grant-unavailable");
    }
    @Test void wrongMachineTenantAndUnboundSubjectAreRejected() {
        db.transaction(tx->{machines.provision(tx,"worker",MachineService.Kind.WORKER,Set.of("security"),"worker-secret-01234567890123456789012345");return null;});
        assertThatThrownBy(()->consoles.exchange("worker-secret-01234567890123456789012345",new ConsoleIdentityService.Exchange(subject,"a".repeat(64)))).hasMessage("unauthenticated");
        assertThatThrownBy(()->consoles.exchange(MACHINE,new ConsoleIdentityService.Exchange(new ConsoleIdentityService.Subject("2","admin",subject.hostUserId()),"a".repeat(64)))).hasMessage("console-identity-unbound");
        assertThatThrownBy(()->db.transaction(tx->consoles.bind(tx,new Principal(account.userId(),"operator","x",1,false),new ConsoleIdentityService.Bind("host",subject,account.userId(),1,true)))).hasMessage("forbidden");
    }
    @Test void recoveryNeedsOperationPasswordAndOneTimeCodeAndRevokesOldSessions()throws Exception {
        enroll();String old=session;
        assertThatThrownBy(()->identity.recoverConsoleFactor(session,"wrong",recovery.getFirst())).hasMessage("authentication-failed");
        var renewed=identity.recoverConsoleFactor(session,OP,recovery.getFirst());
        assertThatThrownBy(()->identity.principal(old)).hasMessage("unauthenticated");
        session=exchange("a");assertThat(consoles.status(session).enrolled()).isFalse();
        secret=identity.beginConsoleEnrollment(session,OP,renewed.enrollmentToken()).secret();
        identity.completeConsoleEnrollment(session,OP,renewed.enrollmentToken(),otp());session=exchange("a");
        assertThatThrownBy(()->identity.recoverConsoleFactor(session,OP,recovery.getFirst())).hasMessage("authentication-failed");
    }
    @Test void administratorCanResetAnotherIdentityWithoutItsUnknownLoginPassword()throws Exception {
        enroll();var accounts=new AccountManagement(db,identity,credentials,vault);var actor=identity.principal(session);
        var target=db.transaction(tx->identity.createAccount(tx,actor.userId(),"second",credentials.randomToken(),OP,false));
        String replacement="ReplacementOperation!456";
        db.transaction(tx->accounts.resetFactor(tx,actor,new AccountManagement.Reset(target.userId(),1,replacement)));
        var updated=db.transaction(tx->tx.get("user",target.userId(),User.class).orElseThrow());
        assertThat(updated.credentialVersion()).isEqualTo(2);assertThat(updated.enrolled()).isFalse();
        assertThat(credentials.passwordMatches(replacement,updated.operationHash())).isTrue();
        assertThat(credentials.passwordMatches(OP,updated.operationHash())).isFalse();
        assertThatThrownBy(()->db.transaction(tx->accounts.resetFactor(tx,actor,new AccountManagement.Reset(actor.userId(),actor.credentialVersion(),replacement)))).hasMessage("use-self-recovery-for-own-factor");
    }
    @Test void refreshReusesBoundSessionButLeaseAndRevocationsInvalidateIt()throws Exception {
        enroll();assertThat(exchange("a")).isEqualTo(session);
        clock.advance(60);assertThat(exchange("a")).isEqualTo(session);
        db.transaction(tx->{consoles.bindLocal(tx,"operator",new ConsoleIdentityService.Bind("host",subject,account.userId(),1,false));return null;});
        assertThatThrownBy(()->identity.principal(session)).hasMessage("unauthenticated");
        assertThatThrownBy(()->exchange("a")).hasMessage("console-identity-disabled");
        db.transaction(tx->{consoles.bindLocal(tx,"operator",new ConsoleIdentityService.Bind("host",subject,account.userId(),2,true));return null;});
        session=exchange("a");clock.advance(91);
        assertThatThrownBy(()->identity.principal(session)).hasMessage("unauthenticated");
        assertThat(exchange("a")).isNotEqualTo(session);
    }
    @Test void machineRotationAndCredentialChangesRevokePendingProofs()throws Exception {
        enroll();var challenge=identity.challenge(session,intent());
        db.transaction(tx->{var m=tx.get("machine","host",MachineService.Machine.class).orElseThrow();tx.put("machine","host",new MachineService.Machine(m.id(),m.kind(),m.tokenHash(),m.environments(),true,m.revision()+1));return null;});
        boolean pendingActorActive=db.transaction(tx->ConsoleIdentityService.originActive(tx,tx.get("session",credentials.tokenHash(session),Session.class).orElseThrow()));
        assertThat(pendingActorActive).isFalse();
        assertThatThrownBy(()->identity.authorize(session,challenge.id(),OP,otp())).hasMessage("unauthenticated");
        session=exchange("a");
        db.transaction(tx->{var u=tx.get("user",account.userId(),User.class).orElseThrow();tx.put("user",u.id(),new User(u.id(),u.username(),u.loginHash(),u.operationHash(),u.sealedTotp(),u.usedTotpStep(),u.credentialVersion()+1,true,u.enrolled(),u.securityAdmin(),null,null,u.recoveryHashes()));return null;});
        assertThatThrownBy(()->identity.principal(session)).hasMessage("unauthenticated");
    }
    static class MutableClock extends Clock {
        Instant value=Instant.parse("2026-01-01T00:00:00Z");void advance(long seconds){value=value.plusSeconds(seconds);}
        @Override public Instant instant(){return value;}@Override public ZoneId getZone(){return ZoneOffset.UTC;}@Override public Clock withZone(ZoneId zone){return this;}
    }
}
