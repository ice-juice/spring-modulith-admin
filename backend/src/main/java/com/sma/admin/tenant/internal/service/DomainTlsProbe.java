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

/** Fixed-address TLS with actual DNS-name verification. No HTTP client, redirects or second DNS lookup. */
@Component
public class DomainTlsProbe implements AutoCloseable {
    private static final DateTimeFormatter CERT_TIME=DateTimeFormatter.ofPattern("uuuuMMddHHmmss'GMT'XXX",Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
    private final Supplier<SSLSocketFactory> factories;
    private final int port;
    private final ScheduledThreadPoolExecutor deadlines=new ScheduledThreadPoolExecutor(1,Thread.ofPlatform().daemon().name("sma-domain-deadline-",0).factory());
    @Autowired public DomainTlsProbe() { this(DomainTlsProbe::freshFactory,443); }
    /** Explicit transport seam for isolated TLS fixtures; production always uses port 443 and JVM trust. */
    public DomainTlsProbe(Supplier<SSLSocketFactory> factories,int port) {
        this.factories=Objects.requireNonNull(factories);this.port=port;
        if(port<1 || port>65535) throw new IllegalArgumentException("Invalid TLS port");
        deadlines.setRemoveOnCancelPolicy(true);
    }
    public record Evidence(String address,Instant validUntil,String fingerprint) {}
    public Evidence inspect(String hostname,InetAddress approvedAddress,Duration connectTimeout,Duration remaining) throws IOException {
        long millis=remaining.toMillis();if(millis<1 || millis>12000) throw new IOException("TLS deadline exceeded");
        try(var socket=new Socket()) {
            var expiry=deadlines.schedule(()->{try{socket.close();}catch(IOException ignored){}},millis,TimeUnit.MILLISECONDS);
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
            } finally { expiry.cancel(false); }
        }
    }
    private static SSLSocketFactory freshFactory() {
        try { var context=SSLContext.getInstance("TLS");context.init(null,null,null);return context.getSocketFactory(); }
        catch(GeneralSecurityException failure) { throw new IllegalStateException("TLS verification is unavailable",failure); }
    }
    @Override @PreDestroy public void close() { deadlines.shutdownNow(); }
}
