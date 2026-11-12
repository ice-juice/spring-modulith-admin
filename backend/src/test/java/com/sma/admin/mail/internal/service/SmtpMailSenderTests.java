package com.sma.admin.mail.internal.service;

import static org.assertj.core.api.Assertions.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.net.ssl.*;
import org.junit.jupiter.api.*;
import com.sma.admin.common.internal.security.*;
import com.sma.admin.common.security.*;
import com.sma.admin.mail.internal.persistence.MailProfileRevisionEntity;

/** A local TLS SMTP peer. It receives only synthetic messages and never contacts an external mail service. */
class SmtpMailSenderTests {
    private static Path directory;
    private static SSLContext trusted,original;
    private static TenantCredentialCrypto secrets;
    @BeforeAll static void setup()throws Exception{
        directory=Files.createTempDirectory("sma-smtp-test-");Path store=directory.resolve("server.p12");
        var command=List.of(Path.of(System.getProperty("java.home"),"bin","keytool.exe").toString(),"-genkeypair","-alias","server",
                "-keyalg","RSA","-keysize","2048","-storetype","PKCS12","-keystore",store.toString(),"-storepass","local-test-only",
                "-dname","CN=localhost","-ext","SAN=dns:localhost","-validity","2","-noprompt");
        var process=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(directory.resolve("keytool.log").toFile()).start();
        if(!process.waitFor(30,TimeUnit.SECONDS)||process.exitValue()!=0)throw new IllegalStateException("Local test certificate creation failed");
        KeyStore keys=KeyStore.getInstance("PKCS12");try(var in=Files.newInputStream(store)){keys.load(in,"local-test-only".toCharArray());}
        var km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(keys,"local-test-only".toCharArray());
        var tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(keys);
        trusted=SSLContext.getInstance("TLS");trusted.init(km.getKeyManagers(),tm.getTrustManagers(),new SecureRandom());
        original=SSLContext.getDefault();SSLContext.setDefault(trusted);
        byte[] key=new byte[32];new SecureRandom().nextBytes(key);Path ring=directory.resolve("secrets.keys");
        Files.writeString(ring,"active=test\nkey.test="+Base64.getEncoder().encodeToString(key));
        secrets=new TenantCredentialCrypto(new TenantCredentialProperties(ring.toString()));
    }
    @AfterAll static void cleanup()throws Exception{
        if(original!=null)SSLContext.setDefault(original);if(secrets!=null)secrets.destroy();
        if(directory!=null)try(var paths=Files.walk(directory)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}
    }
    private SmtpProperties settings(int port,String host,Duration timeout){return new SmtpProperties(
            List.of(new SmtpProperties.Route(host,Set.of(port),Set.of("127.0.0.1","0:0:0:0:0:0:0:1"))),Duration.ofSeconds(2),Duration.ofSeconds(2),Duration.ofSeconds(2),timeout,1);}
    private MailWorkService.Prepared prepared(int port,String host,String tls){
        return ActorContext.withinJob(951,"smtp-test",()->{
            var p=new MailProfileRevisionEntity();p.setTenantId(951L);p.setProfileId(17L);p.setConfigurationVersion(2L);p.setCredentialVersion(1L);
            p.setHost(host);p.setPort(port);p.setTlsMode(tls);p.setUsername("mailer");p.setFromAddress("from@example.test");p.setFromName("测试发件人");
            p.setSecretEnvelope(secrets.encrypt(new TenantConfigurationRef(951,"mail",17,2,1),"smtp-password","password".toCharArray()));
            return new MailWorkService.Prepared(new MailWorkService.Claim(951,99,1,"worker"),"stable-message@example.test",
                    new MailPayload("to@example.test","本地测试","验证码 123456",false),p);
        });
    }
    private MailWorkService.Outcome send(Peer peer,String host,String tls,Duration deadline)throws Exception{
        var settings=settings(peer.port(),host,deadline);
        var sender=new SmtpMailSender(settings,new SmtpNetworkPolicy(settings),secrets,new MailWorkProperties(0,0,null));
        var complete=new CountDownLatch(1);
        try{
            var outcome=ActorContext.withinJob(951,"smtp-test",()->sender.send(prepared(peer.port(),host,tls),complete::countDown));
            assertThat(complete.await(3,TimeUnit.SECONDS)).as("actual TLS transport exited").isTrue();
            return outcome;
        }finally{sender.close();}
    }
    @Test void implicitTlsAndStartTlsVerifyCertificatesAndDeliverUtf8WithStableId()throws Exception{
        for(boolean implicit:List.of(false,true))try(var peer=new Peer(implicit,"ACCEPT")){
            assertThat(send(peer,"localhost",implicit?"TLS":"STARTTLS",Duration.ofSeconds(10))).isEqualTo(MailWorkService.Outcome.ACCEPTED);
            String message=peer.message.get(3,TimeUnit.SECONDS);
            assertThat(message).contains("Message-ID: <stable-message@example.test>","Content-Type: text/plain; charset=UTF-8");
            var mime=new jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(new Properties()),new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8)));
            assertThat(mime.getSubject()).isEqualTo("本地测试");assertThat(mime.getContent().toString()).contains("123456");
            assertThat(peer.authenticated.get()).isTrue();assertThat(peer.tlsActive.get()).isTrue();
        }
    }
    @Test void hostnameMismatchAndMissingStartTlsNeverSendCredentialsOrData()throws Exception{
        try(var peer=new Peer(true,"ACCEPT")){
            assertThat(send(peer,"127.0.0.1","TLS",Duration.ofSeconds(5))).isEqualTo(MailWorkService.Outcome.FAILED);
            assertThat(peer.authenticated.get()).isFalse();assertThat(peer.message.isDone()).isFalse();
        }
        try(var peer=new Peer(false,"NO_TLS")){
            assertThat(send(peer,"localhost","STARTTLS",Duration.ofSeconds(5))).isNotEqualTo(MailWorkService.Outcome.ACCEPTED);
            assertThat(peer.authenticated.get()).isFalse();assertThat(peer.message.isDone()).isFalse();
        }
    }
    @Test void explicitRejectionIsRetryableOrPermanentButLostDataReplyIsUnknown()throws Exception{
        for(var entry:Map.of("TEMPFAIL",MailWorkService.Outcome.RETRYABLE,"REJECT",MailWorkService.Outcome.FAILED,"DROP",MailWorkService.Outcome.UNKNOWN).entrySet())
            try(var peer=new Peer(false,entry.getKey())){assertThat(send(peer,"localhost","STARTTLS",Duration.ofSeconds(8))).isEqualTo(entry.getValue());}
    }
    @Test void totalDeadlineClosesBlockedConnectionWithoutRetryingPossibleDelivery()throws Exception{
        try(var peer=new Peer(false,"HANG")){
            long start=System.nanoTime();assertThat(send(peer,"localhost","STARTTLS",Duration.ofSeconds(1))).isEqualTo(MailWorkService.Outcome.UNKNOWN);
            assertThat(Duration.ofNanos(System.nanoTime()-start)).isLessThan(Duration.ofSeconds(4));
            assertThat(peer.disconnected.await(3,TimeUnit.SECONDS)).as("peer observed socket closure").isTrue();
        }
    }
    @Test void untrustedCertificateIsRejectedAndNoCredentialsAreSent()throws Exception{
        var untrusted=SSLContext.getInstance("TLS");var trust=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        KeyStore empty=KeyStore.getInstance(KeyStore.getDefaultType());empty.load(null,null);trust.init(empty);
        untrusted.init(null,trust.getTrustManagers(),new SecureRandom());SSLContext.setDefault(untrusted);
        try(var peer=new Peer(true,"ACCEPT")){
            assertThat(send(peer,"localhost","TLS",Duration.ofSeconds(5))).isEqualTo(MailWorkService.Outcome.FAILED);
            assertThat(peer.authenticated.get()).isFalse();assertThat(peer.message.isDone()).isFalse();
        }finally{SSLContext.setDefault(trusted);}
    }
    private static final class Peer implements AutoCloseable {
        final ServerSocket listener;final ExecutorService executor=Executors.newSingleThreadExecutor();
        final CompletableFuture<String> message=new CompletableFuture<>();final AtomicBoolean authenticated=new AtomicBoolean(),tlsActive=new AtomicBoolean();
        final CountDownLatch disconnected=new CountDownLatch(1);
        volatile Socket client;final String behavior;final boolean implicit;
        Peer(boolean implicit,String behavior)throws Exception{
            this.behavior=behavior;this.implicit=implicit;
            listener=implicit?trusted.getServerSocketFactory().createServerSocket(0,1,InetAddress.getByName("127.0.0.1")):new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"));
            executor.submit(()->{try{serve();}catch(Exception expectedDisconnect){/* rejection and cancellation deliberately disconnect */}finally{disconnected.countDown();}});
        }
        int port(){return listener.getLocalPort();}
        void serve()throws Exception{
            client=listener.accept();client.setSoTimeout(5000);
            if(implicit){((SSLSocket)client).startHandshake();tlsActive.set(true);}
            var in=reader();var out=writer();line(out,"220 localhost SMTP test");
            String command;
            while((command=in.readLine())!=null){
                if(command.startsWith("EHLO")){line(out,"250-localhost");if(!tlsActive.get()&&!behavior.equals("NO_TLS"))line(out,"250-STARTTLS");line(out,"250 AUTH LOGIN PLAIN");}
                else if(command.equals("STARTTLS")){
                    line(out,"220 TLS ready");client=trusted.getSocketFactory().createSocket(client,"localhost",client.getPort(),true);
                    ((SSLSocket)client).setUseClientMode(false);((SSLSocket)client).startHandshake();tlsActive.set(true);in=reader();out=writer();
                } else if(command.startsWith("AUTH LOGIN")){
                    if(!tlsActive.get())throw new IllegalStateException("Credentials before TLS");
                    line(out,"334 VXNlcm5hbWU6");String user=new String(Base64.getDecoder().decode(in.readLine()),StandardCharsets.UTF_8);
                    line(out,"334 UGFzc3dvcmQ6");String pass=new String(Base64.getDecoder().decode(in.readLine()),StandardCharsets.UTF_8);
                    authenticated.set(user.equals("mailer")&&pass.equals("password"));line(out,authenticated.get()?"235 authenticated":"535 rejected");
                } else if(command.startsWith("MAIL FROM"))line(out,"250 sender ok");
                else if(command.startsWith("RCPT TO"))line(out,behavior.equals("TEMPFAIL")?"450 retry later":behavior.equals("REJECT")?"550 rejected":"250 recipient ok");
                else if(command.equals("DATA")){
                    line(out,"354 send data");StringBuilder body=new StringBuilder();while((command=in.readLine())!=null&&!command.equals("."))body.append(command).append("\r\n");
                    message.complete(body.toString());if(behavior.equals("DROP"))return;
                    if(behavior.equals("HANG")){while(in.read()!=-1){}return;}line(out,"250 accepted");
                } else if(command.equals("RSET"))line(out,"250 reset");
                else if(command.equals("QUIT")){line(out,"221 bye");return;}
                else line(out,"500 unsupported");
            }
        }
        BufferedReader reader()throws IOException{return new BufferedReader(new InputStreamReader(client.getInputStream(),StandardCharsets.UTF_8));}
        BufferedWriter writer()throws IOException{return new BufferedWriter(new OutputStreamWriter(client.getOutputStream(),StandardCharsets.UTF_8));}
        void line(BufferedWriter out,String value)throws IOException{out.write(value+"\r\n");out.flush();}
        public void close()throws Exception{listener.close();if(client!=null)client.close();executor.shutdownNow();executor.awaitTermination(5,TimeUnit.SECONDS);}
    }
}
