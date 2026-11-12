package com.sma.admin.defense;
import static org.assertj.core.api.Assertions.*;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.*;
import org.springframework.core.io.ClassPathResource;
import com.sma.admin.defense.internal.service.*;
import com.sma.admin.common.deployment.DeploymentEvidence;
class DefenseStorageConfigurationTests {
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    @org.springframework.boot.context.properties.EnableConfigurationProperties(DefenseStorageProperties.class)
    static class Binding {}
    @Test void configurationDefaultsAndInvalidBudgetsAreValidated(){
        var runner=new org.springframework.boot.test.context.runner.ApplicationContextRunner().withUserConfiguration(Binding.class);
        runner.run(context->{assertThat(context).hasNotFailed();assertThat(context.getBean(DefenseStorageProperties.class).retention()).isEqualTo(Duration.ofDays(30));});
        for(String input:new String[]{"sma.defense.storage.maximum-platform-records=0","sma.defense.storage.maximum-tenant-total-records=1",
                "sma.defense.storage.retention=0s","sma.defense.storage.cleanup-batch=101"})runner.withPropertyValues(input).run(context->assertThat(context).hasFailed());
    }
    @Test void environmentNamesAndEveryBudgetParticipateInDeploymentEvidence()throws Exception{
        var environment=new StandardEnvironment();environment.getPropertySources().addFirst(new MapPropertySource("test",Map.of(
                "SMA_DEFENSE_MAXIMUM_PLATFORM_RECORDS","5","SMA_DEFENSE_MAXIMUM_TENANT_RECORDS","3","SMA_DEFENSE_MAXIMUM_TENANT_TOTAL_RECORDS","8",
                "SMA_DEFENSE_RETENTION","2h","SMA_DEFENSE_CLEANUP_BATCH","2")));
        for(var source:new YamlPropertySourceLoader().load("application",new ClassPathResource("application.yml")))environment.getPropertySources().addLast(source);
        var value=Binder.get(environment).bind("sma.defense.storage",Bindable.of(DefenseStorageProperties.class)).get();
        assertThat(value).isEqualTo(new DefenseStorageProperties(5L,3L,8L,Duration.ofHours(2),2));
        var challenge=new DeploymentEvidence.Challenge("a".repeat(64),"b".repeat(64),"c".repeat(64));
        var baseline=new DefensePolicyEvidence(value).collect(challenge);
        for(var changed:java.util.List.of(new DefenseStorageProperties(6L,3L,8L,Duration.ofHours(2),2),new DefenseStorageProperties(5L,4L,8L,Duration.ofHours(2),2),
                new DefenseStorageProperties(5L,3L,9L,Duration.ofHours(2),2),new DefenseStorageProperties(5L,3L,8L,Duration.ofHours(3),2),new DefenseStorageProperties(5L,3L,8L,Duration.ofHours(2),3)))
            assertThat(new DefensePolicyEvidence(changed).collect(challenge)).isNotEqualTo(baseline);
    }
}
