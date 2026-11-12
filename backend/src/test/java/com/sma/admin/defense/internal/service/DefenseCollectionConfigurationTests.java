package com.sma.admin.defense.internal.service;
import static org.assertj.core.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.*;
import org.springframework.core.io.ClassPathResource;
import com.sma.admin.common.deployment.DeploymentEvidence;
class DefenseCollectionConfigurationTests {
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    @org.springframework.boot.context.properties.EnableConfigurationProperties(DefenseCollectionProperties.class)
    static class Binding {}
    @Test void explicitOptInRequiresPrivateLocationsAndNodeScope(){
        var runner=new org.springframework.boot.test.context.runner.ApplicationContextRunner().withUserConfiguration(Binding.class);
        runner.run(context->{assertThat(context).hasNotFailed();assertThat(context.getBean(DefenseCollectionProperties.class).enabled()).isFalse();});
        for(String[] inputs:new String[][]{{"sma.defense.collection.enabled=true"},{"sma.defense.collection.memory-records=3"},
                {"sma.defense.collection.per-second=0"},{"sma.defense.collection.disk-bytes=1024"},{"sma.defense.collection.retry-delay=0ms"}})
            runner.withPropertyValues(inputs).run(context->assertThat(context).hasFailed());
        runner.withPropertyValues("sma.defense.collection.enabled=true","sma.defense.collection.directory=private-spool","sma.defense.collection.key-file=private-key",
                "sma.defense.collection.scope=staging.node-a").run(context->{assertThat(context).hasNotFailed();assertThat(context.getBean(DefenseCollectionProperties.class).toString()).doesNotContain("private-spool","private-key");});
    }
    @Test void documentedEnvironmentAndPolicyEvidenceIncludeBudgetsButExcludeNodePaths()throws Exception{
        var env=new StandardEnvironment();env.getPropertySources().addFirst(new MapPropertySource("test",Map.of(
                "SMA_DEFENSE_COLLECTION_ENABLED","true","SMA_DEFENSE_COLLECTION_DIRECTORY","private-spool","SMA_DEFENSE_COLLECTION_KEY_FILE","private-key",
                "SMA_DEFENSE_COLLECTION_SCOPE","staging.node-a","SMA_DEFENSE_COLLECTION_MEMORY_RECORDS","16","SMA_DEFENSE_COLLECTION_DISK_BYTES","16384")));
        for(var source:new YamlPropertySourceLoader().load("application",new ClassPathResource("application.yml")))env.getPropertySources().addLast(source);
        var p=Binder.get(env).bind("sma.defense.collection",Bindable.of(DefenseCollectionProperties.class)).get();
        assertThat(p.enabled()).isTrue();assertThat(p.scope()).isEqualTo("staging.node-a");assertThat(p.memoryRecords()).isEqualTo(16);assertThat(p.diskBytes()).isEqualTo(16384);
        var storage=new DefenseStorageProperties(null,null,null,null,null);var challenge=new DeploymentEvidence.Challenge("a".repeat(64),"b".repeat(64),"c".repeat(64));
        var baseline=new DefensePolicyEvidence(storage,p).collect(challenge);
        var moved=new DefenseCollectionProperties(p.enabled(),"other-path","other-key",p.memoryRecords(),p.diskRecords(),p.diskBytes(),p.perSecond(),p.batch(),p.retryDelay(),"staging.node-b");
        assertThat(new DefensePolicyEvidence(storage,moved).collect(challenge)).isEqualTo(baseline);
        var budget=new DefenseCollectionProperties(p.enabled(),p.directory(),p.keyFile(),32,p.diskRecords(),p.diskBytes(),p.perSecond(),p.batch(),p.retryDelay(),p.scope());
        assertThat(new DefensePolicyEvidence(storage,budget).collect(challenge)).isNotEqualTo(baseline);
    }
}
