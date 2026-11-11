package com.sma.release;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sma.extension.protocol.CanaryObservation;
import com.sma.release.deployment.CanaryGuard;

class CanaryGuardTests {
    @Test void completeAllNodeWindowAndSufficientSamplesAreRequiredBeforeHealthPasses() {
        var guard=new CanaryGuard.Request("deployment",2,100,100,500,false,Instant.now().plusSeconds(600));long minute=100;
        var one=sample("one",minute,50,0,0,true);var two=sample("two",minute,50,0,0,true);
        assertThat(CanaryGuard.assess(guard,minute,List.of("one","two"),List.of(one)).state()).isEqualTo("MISSING_NODES");
        assertThat(CanaryGuard.assess(guard,minute,List.of("one","two"),List.of(one,sample("two",minute,50,0,0,false))).state()).isEqualTo("MISSING_NODES");
        assertThat(CanaryGuard.assess(guard,minute,List.of("one"),List.of(one)).state()).isEqualTo("INSUFFICIENT_SAMPLES");
        assertThat(CanaryGuard.assess(guard,minute,List.of("one","two"),List.of(one,two)).state()).isEqualTo("HEALTHY");
        assertThat(CanaryGuard.assess(guard,minute,List.of("one","two"),List.of(one,sample("two",minute,50,2,0,true))).state()).isEqualTo("BREACHED");
        assertThat(CanaryGuard.assess(guard,minute,List.of("one","two"),List.of(one,sample("two",minute,50,0,6,true))).state()).isEqualTo("BREACHED");
        assertThat(CanaryGuard.assess(guard,minute+1,List.of("one","two"),List.of(one,two)).state()).isEqualTo("MISSING_NODES");
    }
    private CanaryGuard.Sample sample(String node,long minute,long calls,long failures,long slow,boolean complete){return new CanaryGuard.Sample(node,node,"production",1,new CanaryObservation("deployment",2,"candidate",minute,calls,failures,slow,complete),Instant.now());}
}
