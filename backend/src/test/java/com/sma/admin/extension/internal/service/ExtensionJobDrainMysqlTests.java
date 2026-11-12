package com.sma.admin.extension.internal.service;

import static org.assertj.core.api.Assertions.*;
import static com.sma.admin.support.DeploymentWorkFixture.host;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;
import com.sma.admin.common.BizException;
import com.sma.admin.common.deployment.DeploymentWork;
import com.sma.admin.common.extension.ExtensionJobPolicy;
import com.sma.admin.extension.ExtensionErrorCode;
import com.sma.admin.support.IntegrationTest;
import com.sma.extension.protocol.Hashes;
import com.sma.extension.protocol.ExecutionDocuments.Member;
import com.sma.extension.runtime.PluginRuntime;

@IntegrationTest @Import(ExtensionJobDrainMysqlTests.Fixture.class)
class ExtensionJobDrainMysqlTests {
    static final String POLICY="drain-job",PERMISSION="extension:invoke:example",KEY="original-job-intent";
    static final long TENANT=97290;
    static final byte[] INPUT="{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    static class Policy implements ExtensionJobPolicy {
        final AtomicInteger resolved=new AtomicInteger();
        public String id(){return POLICY;}
        public Set<String> permissions(){return Set.of(PERMISSION);}
        public Job resolve(String id){resolved.incrementAndGet();assertThat(WorkCaller.current().tenantId()).isEqualTo(TENANT);return new Job("deployment","example","execute",KEY,INPUT);}
    }
    @TestConfiguration(proxyBeanMethods=false) static class Fixture {@Bean Policy drainPolicy(){return new Policy();}}
    @Autowired ExtensionRuntimeService service;
    @Autowired ExtensionWorkJournal journal;
    @Autowired DeploymentWork admission;
    @Autowired ApplicationEventPublisher events;
    @Autowired Policy policy;

    @Test void refusedJobDoesNotResolveOrClaimAndRecoveryReadsOriginalUnknownWithoutReexecution(){
        // Runtime references are platform-wide; do not reuse another test's artifact digest.
        String hash=Hashes.sha256("p08-extension-job-drain-fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var saved=host(()->WorkCaller.job(TENANT,POLICY,Set.of(PERMISSION),()->{
            var received=journal.accept("deployment","example","execute",PERMISSION,KEY,Hashes.sha256(INPUT),
                    new PluginRuntime.Selection("deployment",1,"bundle"),List.of(new Member("example","artifact",hash)));
            var claimed=journal.claim(received.id());journal.unknown(claimed.id(),claimed.claimId(),"unknown-fixture");return journal.get(claimed.id());
        }));
        try{
            AvailabilityChangeEvent.publish(events,this,ReadinessState.REFUSING_TRAFFIC);
            assertThatThrownBy(()->host(()->service.invokeJob(POLICY,TENANT,"object"))).isInstanceOfSatisfying(BizException.class,
                    error->assertThat(error.getErrorCode()).isEqualTo(ExtensionErrorCode.UNAVAILABLE));
            assertThat(policy.resolved).hasValue(0);assertThat(admission.snapshot().activeWork()).isZero();
            AvailabilityChangeEvent.publish(events,this,ReadinessState.ACCEPTING_TRAFFIC);
            var result=host(()->service.invokeJob(POLICY,TENANT,"object"));
            assertThat(result.id()).isEqualTo(saved.id());assertThat(result.state()).isEqualTo("UNKNOWN");assertThat(policy.resolved).hasValue(1);
            var after=host(()->WorkCaller.job(TENANT,POLICY,Set.of(PERMISSION),()->journal.get(saved.id())));
            assertThat(after.claimId()).isEqualTo(saved.claimId());assertThat(after.selection()).isEqualTo(saved.selection());
            assertThat(journal.references(hash)).isEqualTo(1);assertThat(admission.snapshot().activeWork()).isZero();
            assertThat(service.status().versions()).isEmpty(); // no runtime prepared or plugin invoked for UNKNOWN
        }finally{AvailabilityChangeEvent.publish(events,this,ReadinessState.ACCEPTING_TRAFFIC);}
    }
}
