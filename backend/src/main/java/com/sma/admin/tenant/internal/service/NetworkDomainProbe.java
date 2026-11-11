package com.sma.admin.tenant.internal.service;

import java.io.IOException;
import java.net.InetAddress;
import java.time.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import javax.naming.NamingException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import com.sma.admin.common.security.TenantHost;

@Component
@EnableConfigurationProperties(DomainVerificationProperties.class)
public class NetworkDomainProbe implements DomainProbe {
    private final DomainVerificationProperties properties;
    private final DomainDns dns;
    private final DomainTlsProbe tls;
    private final Semaphore permits=new Semaphore(4);
    public NetworkDomainProbe(DomainVerificationProperties properties,DomainDns dns,DomainTlsProbe tls) { this.properties=properties;this.dns=dns;this.tls=tls; }
    @Override public Result verify(String hostname,String challenge) {
        if(properties.ingressAddresses().isEmpty()) return fail(false,false,Failure.NOT_CONFIGURED);
        try {
            if(!TenantHost.domain(hostname).equals(hostname) || !hostname.contains(".") || hostname.matches("[0-9.]+")
                    || hostname.length()>241 || challenge==null || !challenge.matches("sma-tenant-v1=[a-zA-Z0-9_-]{32,64}")) return fail(false,false,Failure.INVALID_DOMAIN);
        } catch(IllegalArgumentException invalid) { return fail(false,false,Failure.INVALID_DOMAIN); }
        if(!permits.tryAcquire()) return fail(false,false,Failure.BUSY);
        long deadline=System.nanoTime()+properties.totalTimeout().toNanos();
        boolean ownership=false,routing=false;
        try {
            var records=dns.txt("_sma-tenant."+hostname,dnsBudget(deadline));
            if(!records.contains(challenge) && !records.contains('"'+challenge+'"')) return fail(false,false,Failure.OWNERSHIP_MISMATCH);
            ownership=true;
            var addresses=dns.addresses(hostname,dnsBudget(deadline));
            if(addresses.isEmpty() || addresses.size()>8 || addresses.stream().map(InetAddress::getHostAddress).anyMatch(value->!properties.ingressAddresses().contains(value)))
                return fail(true,false,Failure.DNS_NOT_ALLOWED);
            routing=true;
            Instant until=null;List<String> fingerprints=new ArrayList<>(),checked=new ArrayList<>();
            for(var address:addresses) {
                long remaining=deadline-System.nanoTime();if(remaining<=0) return fail(true,true,Failure.TIMEOUT);
                var evidence=tls.inspect(hostname,address,properties.connectTimeout(),Duration.ofNanos(remaining));
                if(until==null || evidence.validUntil().isBefore(until)) until=evidence.validUntil();
                checked.add(evidence.address());fingerprints.add(evidence.fingerprint());
            }
            if(System.nanoTime()>deadline || until==null || !until.isAfter(Instant.now())) return fail(true,true,Failure.TIMEOUT);
            return new Result(true,true,true,until,checked,fingerprints,Failure.NONE);
        } catch(NamingException failure) { return fail(ownership,routing,System.nanoTime()>=deadline?Failure.TIMEOUT:Failure.DNS_UNAVAILABLE); }
        catch(IOException | IllegalStateException failure) { return fail(ownership,routing,System.nanoTime()>=deadline?Failure.TIMEOUT:Failure.TLS_FAILED); }
        finally { permits.release(); }
    }
    private Duration dnsBudget(long deadline) throws NamingException {
        long remaining=deadline-System.nanoTime();if(remaining<=0) throw new NamingException("Domain verification deadline exceeded");
        return Duration.ofNanos(Math.min(remaining,properties.dnsTimeout().toNanos()));
    }
    private Result fail(boolean ownership,boolean routing,Failure reason) { return new Result(ownership,routing,false,null,List.of(),List.of(),reason); }
}
