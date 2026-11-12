package com.sma.admin.tenant.internal.service;

import java.io.IOException;
import java.net.*;
import java.security.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import javax.net.ssl.*;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.sma.admin.common.deployment.*;

/** Fixed-address TLS with actual DNS-name verification. No HTTP client, redirects or second DNS lookup. */
@Component
public class DomainTlsProbe implements AutoCloseable,DeploymentActivity,DeploymentTransport {
    private static final DateTimeFormatter CERT_TIME=DateTimeFormatter.ofPattern("uuuuMMddHHmmss'GMT'XXX",Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
    private final Supplier<SSLSocketFactory> factories;
    private final int port;
    private final Supplier<Socket> sockets;
    private final Set<Probe> owned=new HashSet<>();
    private boolean stopping;
    private final DeploymentTaskTracker callbacks=new DeploymentTaskTracker(1024);
    private final ScheduledThreadPoolExecutor deadlines=new ScheduledThreadPoolExecutor(1,Thread.ofPlatform().daemon().inheritInheritableThreadLocals(false).name("sma-domain-deadline-",0).factory());
    @Autowired public DomainTlsProbe() { this(DomainTlsProbe::freshFactory,443); }
    /** Explicit transport seam for isolated TLS fixtures; production always uses port 443 and JVM trust. */
    public DomainTlsProbe(Supplier<SSLSocketFactory> factories,int port) {
        this(factories,port,Socket::new);
    }
    DomainTlsProbe(Supplier<SSLSocketFactory> factories,int port,Supplier<Socket> sockets) {
        this.factories=Objects.requireNonNull(factories);this.port=port;this.sockets=Objects.requireNonNull(sockets);
        if(port<1 || port>65535) throw new IllegalArgumentException("Invalid TLS port");
        deadlines.setRemoveOnCancelPolicy(true);
    }
    @Override public String id(){return "tenant.domain-tls";}
    @Override public synchronized DeploymentActivity.Snapshot snapshot(){
        var activity=callbacks.snapshot();return new DeploymentActivity.Snapshot(activity.queued(),owned.size()+activity.running());
    }
    @Override public void shutdownTransport(){DeploymentEvidence.requireHost();close();}
    @Override public synchronized boolean transportTerminated(){return stopping&&owned.isEmpty()&&callbacks.snapshot().quiescent()&&deadlines.isTerminated();}
    private synchronized Probe beginProbe()throws IOException{
        if(stopping||owned.size()>=1024)throw new IOException("TLS probe unavailable");
        var probe=new Probe();owned.add(probe);return probe;
    }
    private final class Probe implements AutoCloseable {
        private Socket socket;private boolean finished;
        void bind(Socket socket){this.socket=Objects.requireNonNull(socket);}
        @Override public void close(){synchronized(DomainTlsProbe.this){finished=true;retireIfClosed();}}
        void retireIfClosed(){
            synchronized(DomainTlsProbe.this){
                if(finished&&(socket==null||socket.isClosed())){owned.remove(this);shutdownIfIdle();}
            }
        }
    }
    private record Deadline(Future<?> activity,ScheduledFuture<?> scheduled) implements AutoCloseable {
        @Override public void close(){scheduled.cancel(false);activity.cancel(false);}
    }
    private Deadline scheduleDeadline(Probe probe,Socket socket,long millis)throws IOException{
        var scheduled=new java.util.concurrent.atomic.AtomicReference<ScheduledFuture<?>>();
        try{
            var activity=callbacks.submit(task->scheduled.set(deadlines.schedule(task,millis,TimeUnit.MILLISECONDS)),()->{
                try{socket.close();}catch(IOException ignored){}finally{probe.retireIfClosed();}return null;
            });
            return new Deadline(activity,scheduled.get());
        }catch(RejectedExecutionException unavailable){throw new IOException("TLS probe unavailable");}
    }
    /** Caller holds the component monitor; no I/O and no cancellation of admitted probes. */
    private void shutdownIfIdle(){if(stopping&&owned.isEmpty()){callbacks.stopAccepting();deadlines.shutdown();}}
    public record Evidence(String address,Instant validUntil,String fingerprint) {}
    public Evidence inspect(String hostname,InetAddress approvedAddress,Duration connectTimeout,Duration remaining) throws IOException {
        long millis=remaining.toMillis();if(millis<1 || millis>12000) throw new IOException("TLS deadline exceeded");
        try(var probe=beginProbe();var socket=sockets.get()) {
            probe.bind(socket);
            var expiry=scheduleDeadline(probe,socket,millis);
            try {
                socket.connect(new InetSocketAddress(approvedAddress,port),(int)Math.min(connectTimeout.toMillis(),millis));
                socket.setSoTimeout((int)millis);
                try(var tls=(SSLSocket)factories.get().createSocket(socket,hostname,port,true)) {
                    tls.setSoTimeout((int)millis);
                    var parameters=tls.getSSLParameters();
                    parameters.setEndpointIdentificationAlgorithm("HTTPS");
                    parameters.setServerNames(List.of(new SNIHostName(hostname)));
                    tls.setSSLParameters(parameters);tls.startHandshake();
                    try {
                        // Existing BC ASN.1 support reads the JCA-validated certificate without introducing java.util.Date.
                        var chain=tls.getSession().getPeerCertificates();
                        if(chain.length==0 || chain.length>8) throw new IOException("TLS certificate chain exceeds the verification budget");
                        Instant validUntil=null,now=Instant.now();String fingerprint=null;
                        for(var certificate:chain) {
                            byte[] encoded=certificate.getEncoded();
                            if(encoded.length>65536) throw new IOException("TLS certificate exceeds the verification budget");
                            var parsed=org.bouncycastle.asn1.x509.Certificate.getInstance(encoded);
                            var start=OffsetDateTime.parse(parsed.getStartDate().getTime(),CERT_TIME).toInstant();
                            var end=OffsetDateTime.parse(parsed.getEndDate().getTime(),CERT_TIME).toInstant();
                            if(start.isAfter(now) || !end.isAfter(now)) throw new IOException("TLS certificate is outside its validity period");
                            if(validUntil==null || end.isBefore(validUntil)) validUntil=end;
                            if(fingerprint==null) fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded));
                        }
                        return new Evidence(approvedAddress.getHostAddress(),validUntil,fingerprint);
                    } catch(IllegalArgumentException | DateTimeException | GeneralSecurityException invalid) {
                        throw new IOException("Unsupported TLS certificate validity",invalid);
                    }
                }
            } finally { expiry.close(); }
        }
    }
    private static SSLSocketFactory freshFactory() {
        try { var context=SSLContext.getInstance("TLS");context.init(null,null,null);return context.getSocketFactory(); }
        catch(GeneralSecurityException failure) { throw new IllegalStateException("TLS verification is unavailable",failure); }
    }
    @Override @PreDestroy public synchronized void close() { stopping=true;shutdownIfIdle(); }
}
