package com.sma.admin.tenant;

import static org.assertj.core.api.Assertions.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sma.admin.tenant.internal.service.*;

class DomainTlsProbeTests {
    private static final String HOST="p10.example.test";
    private static final char[] PASSWORD="p10-test-only".toCharArray();
    @TempDir static Path directory;
    static KeyStore keys;
    @BeforeAll static void certificate() throws Exception {
        var keytool=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"keytool.exe":"keytool");
        var file=directory.resolve("domain.p12");
        var process=new ProcessBuilder(keytool.toString(),"-genkeypair","-alias","p10","-keyalg","RSA","-keysize","2048","-validity","2",
                "-dname","CN="+HOST,"-ext","SAN=dns:"+HOST,"-storetype","PKCS12","-keystore",file.toString(),"-storepass",new String(PASSWORD),"-noprompt")
                .redirectErrorStream(true).redirectOutput(directory.resolve("keytool.log").toFile()).start();
        if(!process.waitFor(30,TimeUnit.SECONDS)) { process.destroyForcibly();throw new IllegalStateException("TLS fixture certificate generation timed out"); }
        assertThat(process.exitValue()).as("Temporary TLS fixture certificate").isZero();
        keys=KeyStore.getInstance("PKCS12");try(var input=Files.newInputStream(file)){keys.load(input,PASSWORD);}
    }
    private static SSLContext serverContext() throws Exception {
        var managers=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());managers.init(keys,PASSWORD);
        var context=SSLContext.getInstance("TLS");context.init(managers.getKeyManagers(),null,null);return context;
    }
    private static SSLSocketFactory clientFactory(boolean trusted) {
        try {
            var store=KeyStore.getInstance("PKCS12");store.load(null,null);
            if(trusted)store.setCertificateEntry("p10",keys.getCertificate("p10"));
            var managers=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());managers.init(store);
            var context=SSLContext.getInstance("TLS");context.init(null,managers.getTrustManagers(),null);return context.getSocketFactory();
        } catch(Exception failure) { throw new IllegalStateException(failure); }
    }
    @Test void actualTlsUsesPinnedAddressSniTrustAndCertificateExpiry() throws Exception {
        try(var server=new TlsServer();var probe=new DomainTlsProbe(()->clientFactory(true),server.port())) {
            var evidence=probe.inspect(HOST,DomainAddresses.literal("127.0.0.1"),Duration.ofSeconds(1),Duration.ofSeconds(5));
            assertThat(evidence.address()).isEqualTo("127.0.0.1");
            assertThat(evidence.validUntil()).isBetween(Instant.now().plusSeconds(86400),Instant.now().plusSeconds(3*86400));
            assertThat(evidence.fingerprint()).matches("[a-f0-9]{64}");
            assertThat(server.handshake.get(3,TimeUnit.SECONDS)).isEqualTo(HOST);
        }
    }
    @Test void actualTlsRejectsWrongHostnameAndUntrustedCertificate() throws Exception {
        try(var server=new TlsServer();var probe=new DomainTlsProbe(()->clientFactory(true),server.port())) {
            assertThatThrownBy(()->probe.inspect("wrong.example.test",DomainAddresses.literal("127.0.0.1"),Duration.ofSeconds(1),Duration.ofSeconds(5))).isInstanceOf(IOException.class);
        }
        try(var server=new TlsServer();var probe=new DomainTlsProbe(()->clientFactory(false),server.port())) {
            assertThatThrownBy(()->probe.inspect(HOST,DomainAddresses.literal("127.0.0.1"),Duration.ofSeconds(1),Duration.ofSeconds(5))).isInstanceOf(IOException.class);
        }
    }
    @Test void anUnresponsivePeerCannotKeepTheVerificationSocketOpenPastItsDeadline() throws Exception {
        try(var listener=new ServerSocket(0,1,DomainAddresses.literal("127.0.0.1"));var executor=Executors.newSingleThreadExecutor()) {
            var accepted=executor.submit(listener::accept);
            try(var probe=new DomainTlsProbe(()->clientFactory(true),listener.getLocalPort())) {
                long started=System.nanoTime();
                assertThatThrownBy(()->probe.inspect(HOST,DomainAddresses.literal("127.0.0.1"),Duration.ofMillis(100),Duration.ofMillis(300))).isInstanceOf(IOException.class);
                assertThat(Duration.ofNanos(System.nanoTime()-started)).isLessThan(Duration.ofSeconds(3));
                try(var peer=accepted.get(2,TimeUnit.SECONDS)) { assertThat(peer.isConnected()).isTrue(); }
            } finally { listener.close(); }
        }
    }
    private static class TlsServer implements AutoCloseable {
        final SSLServerSocket listener;
        final ExecutorService executor=Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("p10-tls-fixture").factory());
        final AtomicReference<Socket> peer=new AtomicReference<>();
        final Future<String> handshake;
        TlsServer() throws Exception {
            listener=(SSLServerSocket)serverContext().getServerSocketFactory().createServerSocket(0,1,DomainAddresses.literal("127.0.0.1"));
            handshake=executor.submit(()->{
                try(var client=(SSLSocket)listener.accept()) {
                    peer.set(client);client.setSoTimeout(3000);client.startHandshake();
                    var session=(ExtendedSSLSession)client.getSession();
                    return session.getRequestedServerNames().stream().filter(value->value instanceof SNIHostName).map(value->((SNIHostName)value).getAsciiName()).findFirst().orElse("");
                }
            });
        }
        int port() { return listener.getLocalPort(); }
        @Override public void close() throws Exception {
            listener.close();var connected=peer.get();if(connected!=null)connected.close();executor.shutdownNow();
            if(!executor.awaitTermination(3,TimeUnit.SECONDS))throw new IllegalStateException("TLS fixture did not stop");
        }
    }
}
