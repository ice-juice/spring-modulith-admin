package com.sma.release;

import static org.assertj.core.api.Assertions.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.KeyStore;
import java.time.*;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.*;
import org.apache.commons.codec.binary.Base32;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.test.context.*;
import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import com.sma.extension.protocol.*;
import com.sma.release.auth.*;
import com.sma.release.catalog.CatalogRecords;
import com.sma.release.store.ReleaseDatabase;

/** Opt-in dedicated MySQL + real loopback TLS. Never cleans the host database; certificate validation remains enabled. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named="SMA_RELEASE_IT_DB_URL",matches="jdbc:mysql:.*")
@Import(SecurityMysqlHttpsTests.TimeConfig.class)
class SecurityMysqlHttpsTests {
    private static Path directory,store;
    private static String password;
    private static final TestClock CLOCK=new TestClock();
    @LocalServerPort int port;
    @Autowired IdentityService identities;
    @Autowired MachineService machines;
    @Autowired ConsoleIdentityService consoles;
    @Autowired ReleaseDatabase database;
    @TestConfiguration static class TimeConfig {@Bean @Primary Clock testClock(){return CLOCK;}}
    private static class TestClock extends Clock {private Instant instant=Instant.now();public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return instant;}void advance(){instant=instant.plusSeconds(30);}}
    @DynamicPropertySource static void configure(DynamicPropertyRegistry properties)throws Exception {
        String url=System.getenv("SMA_RELEASE_IT_DB_URL"),user=System.getenv("SMA_RELEASE_IT_DB_USERNAME"),secret=System.getenv("SMA_RELEASE_IT_DB_PASSWORD");
        Checks.require(url!=null && URI.create(url.substring(5)).getPath().matches("/sma_test_release_[a-z0-9_]+"),"dedicated-release-test-database-required");
        Flyway.configure().dataSource(url,user,secret).cleanDisabled(false).load().clean();
        directory=Files.createTempDirectory("sma-release-https-test-");store=directory.resolve("test.p12");password=UUID.randomUUID().toString();
        Path keytool=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"keytool.exe":"keytool");
        var process=new ProcessBuilder(keytool.toString(),"-genkeypair","-alias","fixture","-keyalg","RSA","-keysize","2048","-validity","2","-dname","CN=localhost","-ext","SAN=dns:localhost,ip:127.0.0.1","-storetype","PKCS12","-keystore",store.toString(),"-storepass",password).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        assertThat(process.waitFor(30,java.util.concurrent.TimeUnit.SECONDS)).isTrue();assertThat(process.exitValue()).isZero();
        properties.add("spring.datasource.url",()->url);properties.add("spring.datasource.username",()->user);properties.add("spring.datasource.password",()->secret);
        properties.add("release.directory",()->directory.resolve("artifacts").toString());properties.add("release.encryption-key",()->Base64.getEncoder().encodeToString(new byte[32]));properties.add("release.origin",()->"https://localhost");properties.add("release.bootstrap-file",()->"");
        properties.add("server.ssl.enabled",()->true);properties.add("server.ssl.key-store",()->store.toUri().toString());properties.add("server.ssl.key-store-password",()->password);properties.add("server.ssl.key-store-type",()->"PKCS12");
    }
    @Test void realTlsAndIndependentMysqlEnforceMfaCommandsAndAuditAnchors()throws Exception {
        var keys=KeyStore.getInstance("PKCS12");try(var input=Files.newInputStream(store)){keys.load(input,password.toCharArray());}
        var trust=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());trust.init(keys);var ssl=SSLContext.getInstance("TLS");ssl.init(null,trust.getTrustManagers(),null);
        var client=HttpClient.newBuilder().sslContext(ssl).connectTimeout(Duration.ofSeconds(5)).build();
        String login="FixtureLogin!"+UUID.randomUUID(),operation="FixtureOperation!"+UUID.randomUUID();
        var account=identities.bootstrap("https-operator",login,operation);
        var enrollment=post(client,"/auth/enrollment/begin",null,Map.of("username","https-operator","password",login,"enrollmentToken",account.enrollmentToken(),"otp",""),SecurityRecords.Enrollment.class);
        post(client,"/auth/enrollment/complete",null,Map.of("username","https-operator","password",login,"enrollmentToken",account.enrollmentToken(),"otp",otp(enrollment.secret())),String[].class);
        CLOCK.advance();var ticket=post(client,"/auth/login",null,Map.of("username","https-operator","password",login,"otp",otp(enrollment.secret())),SecurityRecords.LoginTicket.class);
        byte[] params=ProtocolJson.write(new CatalogRecords.GroupInput("TLS group",true));var intent=new ActionIntent(ActionIntent.Action.GROUP_CREATE,"production","tls-group",0,"console",0,1,List.of(),Hashes.sha256(params));
        var challenge=post(client,"/challenges",ticket.token(),intent,SecurityRecords.ChallengeView.class);CLOCK.advance();
        var grant=post(client,"/authorizations",ticket.token(),Map.of("challengeId",challenge.id(),"operationPassword",operation,"otp",otp(enrollment.secret())),SecurityRecords.GrantView.class);
        var command=new CatalogRecords.Command("tls-command",grant.token(),intent,params);var result=post(client,"/commands",ticket.token(),command,CatalogRecords.ActionReceipt.class);
        assertThat(post(client,"/commands",ticket.token(),command,CatalogRecords.ActionReceipt.class).id()).isEqualTo(result.id());
        var untrusted=HttpClient.newHttpClient();assertThatThrownBy(() -> untrusted.send(HttpRequest.newBuilder(uri("/groups")).GET().build(),HttpResponse.BodyHandlers.ofString())).isInstanceOf(javax.net.ssl.SSLHandshakeException.class);
        String auditor="a"+UUID.randomUUID().toString().replace("-","");database.transaction(tx->{machines.provision(tx,"tls-auditor",MachineService.Kind.AUDITOR,Set.of("security"),auditor);return null;});
        var response=client.send(HttpRequest.newBuilder(uri("/machines/auditors/audit?after=0&expectedHash="+"0".repeat(64))).header("Authorization","Bearer "+auditor).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);var batch=decode(response.body(),AuditStream.Batch.class);long sequence=0;String hash="0".repeat(64);
        for(var entry:batch.entries()){hash=AuditStream.verify(sequence,hash,entry);sequence=entry.entry().sequence();}
        assertThat(sequence).isPositive();
        post(client,"/machines/auditors/ack",auditor,Map.of("sequence",sequence,"hash",hash),Object.class);
        var forbidden=client.send(HttpRequest.newBuilder(uri("/groups")).header("Authorization","Bearer "+auditor).GET().build(),HttpResponse.BodyHandlers.discarding());assertThat(forbidden.statusCode()).isEqualTo(401);
        // Real HTTPS console exchange: host identity opens reads; exact writes still need independent factors.
        String host="h"+UUID.randomUUID().toString().replace("-","");
        database.transaction(tx->{machines.provision(tx,"tls-console",MachineService.Kind.CONSOLE,Set.of("security"),host);return null;});
        var subject=new ConsoleIdentityService.Subject("1","admin","9007199254740993");
        var binding=new ConsoleIdentityService.Bind("tls-console",subject,account.userId(),0,true);
        byte[] bindingBytes=ProtocolJson.write(binding);
        var bindIntent=new ActionIntent(ActionIntent.Action.IDENTITY_BIND,"security","identity",0,"console",0,1,List.of(),Hashes.sha256(bindingBytes));
        var bindChallenge=post(client,"/challenges",ticket.token(),bindIntent,SecurityRecords.ChallengeView.class);CLOCK.advance();
        var bindGrant=post(client,"/authorizations",ticket.token(),Map.of("challengeId",bindChallenge.id(),"operationPassword",operation,"otp",otp(enrollment.secret())),SecurityRecords.GrantView.class);
        post(client,"/commands",ticket.token(),new CatalogRecords.Command("tls-bind",bindGrant.token(),bindIntent,bindingBytes),CatalogRecords.ActionReceipt.class);
        var linked=post(client,"/machines/consoles/sessions",host,new ConsoleIdentityService.Exchange(subject,"b".repeat(64)),SecurityRecords.LoginTicket.class);
        var read=client.send(HttpRequest.newBuilder(uri("/groups")).header("Authorization","Bearer "+linked.token()).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
        assertThat(read.statusCode()).isEqualTo(200);
        var nextIntent=new ActionIntent(ActionIntent.Action.GROUP_CREATE,"production","tls-linked",0,"console",0,1,List.of(),Hashes.sha256(params));
        var linkedChallenge=post(client,"/challenges",linked.token(),nextIntent,SecurityRecords.ChallengeView.class);CLOCK.advance();
        var linkedGrant=post(client,"/authorizations",linked.token(),Map.of("challengeId",linkedChallenge.id(),"operationPassword",operation,"otp",otp(enrollment.secret())),SecurityRecords.GrantView.class);
        post(client,"/commands",linked.token(),new CatalogRecords.Command("tls-linked-command",linkedGrant.token(),nextIntent,params),CatalogRecords.ActionReceipt.class);
        database.transaction(tx->{consoles.bindLocal(tx,"fixture",new ConsoleIdentityService.Bind("tls-console",subject,account.userId(),1,false));return null;});
        var revoked=client.send(HttpRequest.newBuilder(uri("/groups")).header("Authorization","Bearer "+linked.token()).GET().build(),HttpResponse.BodyHandlers.discarding());assertThat(revoked.statusCode()).isEqualTo(401);
    }
    private URI uri(String path){return URI.create("https://localhost:"+port+"/release/v1"+path);}
    private <T>T post(HttpClient client,String path,String token,Object body,Class<T> type)throws Exception {
        var request=HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(20)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(ProtocolJson.write(body)));if(token!=null)request.header("Authorization","Bearer "+token);
        var response=client.send(request.build(),HttpResponse.BodyHandlers.ofByteArray());assertThat(response.statusCode()).as(path).isEqualTo(200);return decode(response.body(),type);
    }
    private record Reply(int code,String error,String message,Object data) {}
    private <T>T decode(byte[] bytes,Class<T> type){var reply=ProtocolJson.read(bytes,Reply.class);assertThat(reply.code()).isZero();return ProtocolJson.read(ProtocolJson.write(reply.data()),type);}
    private String otp(String secret)throws Exception{return String.format(Locale.ROOT,"%06d",new TimeBasedOneTimePasswordGenerator(Duration.ofSeconds(30),6).generateOneTimePassword(new SecretKeySpec(new Base32().decode(secret),"HmacSHA1"),CLOCK.instant()));}
}
