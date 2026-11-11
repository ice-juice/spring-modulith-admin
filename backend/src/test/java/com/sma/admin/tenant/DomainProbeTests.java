package com.sma.admin.tenant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import com.sma.admin.tenant.internal.service.*;

class DomainProbeTests {
    private static final String HOST="p10.example.test",CHALLENGE="sma-tenant-v1="+"a".repeat(43);
    private final DomainDns dns=mock(DomainDns.class);
    private final DomainTlsProbe tls=mock(DomainTlsProbe.class);
    private NetworkDomainProbe service(Set<String> addresses) { return new NetworkDomainProbe(new DomainVerificationProperties(addresses,null,null,null),dns,tls); }
    @Test void configurationAndInputsFailClosedBeforeAnyNetworkWork() {
        assertThat(service(Set.of()).verify(HOST,CHALLENGE).failure()).isEqualTo(DomainProbe.Failure.NOT_CONFIGURED);
        var service=service(Set.of("127.0.0.1"));
        for(String host:List.of("https://p10.example.test","127.0.0.1","localhost","P10.example.test"))
            assertThat(service.verify(host,CHALLENGE).failure()).isEqualTo(DomainProbe.Failure.INVALID_DOMAIN);
        assertThat(service.verify(HOST,"caller-claims-verified").successful()).isFalse();
        verifyNoInteractions(dns,tls);
        for(String address:List.of("localhost","127.1","0177.0.0.1","256.0.0.1","::1%lo","https://127.0.0.1"))
            assertThatThrownBy(()->DomainAddresses.literal(address)).isInstanceOf(IllegalArgumentException.class);
        assertThat(DomainAddresses.literal("::ffff:127.0.0.1").getHostAddress()).isEqualTo("127.0.0.1");
        assertThatThrownBy(()->new DomainVerificationProperties(Set.of(),Duration.ofSeconds(20),null,null)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void proofAndEveryResolvedAddressAreCheckedBeforeTls() throws Exception {
        var service=service(Set.of("127.0.0.1"));
        when(dns.txt(anyString(),any())).thenReturn(Set.of("another-token"));
        assertThat(service.verify(HOST,CHALLENGE).failure()).isEqualTo(DomainProbe.Failure.OWNERSHIP_MISMATCH);
        verify(dns,never()).addresses(anyString(),any());
        when(dns.txt(anyString(),any())).thenReturn(Set.of('"'+CHALLENGE+'"'));
        when(dns.addresses(anyString(),any())).thenReturn(List.of(DomainAddresses.literal("127.0.0.1"),DomainAddresses.literal("10.0.0.1")));
        var result=service.verify(HOST,CHALLENGE);
        assertThat(result.ownershipVerified()).isTrue();assertThat(result.dnsReady()).isFalse();assertThat(result.failure()).isEqualTo(DomainProbe.Failure.DNS_NOT_ALLOWED);
        verifyNoInteractions(tls);
    }
    @Test void allApprovedEdgesMustPassAndTheEarliestCertificateExpiryWins() throws Exception {
        var first=DomainAddresses.literal("127.0.0.1");var second=DomainAddresses.literal("127.0.0.2");
        var until=Instant.now().plusSeconds(86400);
        when(dns.txt(anyString(),any())).thenReturn(Set.of(CHALLENGE));when(dns.addresses(anyString(),any())).thenReturn(List.of(first,second));
        when(tls.inspect(eq(HOST),eq(first),any(),any())).thenReturn(new DomainTlsProbe.Evidence(first.getHostAddress(),until.plusSeconds(3600),"a".repeat(64)));
        when(tls.inspect(eq(HOST),eq(second),any(),any())).thenReturn(new DomainTlsProbe.Evidence(second.getHostAddress(),until,"b".repeat(64)));
        var service=service(Set.of("127.0.0.1","127.0.0.2"));var result=service.verify(HOST,CHALLENGE);
        assertThat(result.successful()).isTrue();assertThat(result.tlsValidUntil()).isEqualTo(until);assertThat(result.addresses()).containsExactly("127.0.0.1","127.0.0.2");
        when(tls.inspect(eq(HOST),eq(second),any(),any())).thenThrow(new javax.net.ssl.SSLHandshakeException("Certificate mismatch"));
        assertThat(service.verify(HOST,CHALLENGE).failure()).isEqualTo(DomainProbe.Failure.TLS_FAILED);
    }
    @Test void networkWorkHasAFiniteProcessBudgetAndPermitsAreReleasedAfterFailure() throws Exception {
        var service=service(Set.of("127.0.0.1"));var entered=new CountDownLatch(4);var release=new CountDownLatch(1);
        when(dns.txt(anyString(),any())).thenAnswer(invocation->{entered.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new javax.naming.NamingException("Fixture timeout");return Set.of("wrong");});
        try(var executor=Executors.newFixedThreadPool(4)) {
            try {
                var jobs=java.util.stream.IntStream.range(0,4).mapToObj(i->executor.submit(()->service.verify(HOST,CHALLENGE))).toList();
                assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();
                assertThat(service.verify(HOST,CHALLENGE).failure()).isEqualTo(DomainProbe.Failure.BUSY);
                release.countDown();for(var job:jobs)assertThat(job.get(3,TimeUnit.SECONDS).failure()).isEqualTo(DomainProbe.Failure.OWNERSHIP_MISMATCH);
                assertThat(service.verify(HOST,CHALLENGE).failure()).isEqualTo(DomainProbe.Failure.OWNERSHIP_MISMATCH);
            } finally { release.countDown(); }
        }
    }
}
