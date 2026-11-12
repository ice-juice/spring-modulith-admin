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

class DefenseMaintenanceConfigurationTests {
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    @org.springframework.boot.context.properties.EnableConfigurationProperties(DefenseMaintenanceProperties.class)
    static class Binding {}
    @Test void defaultsAndInvalidMaintenanceBoundsAreBoundBySpring(){
        var runner=new org.springframework.boot.test.context.runner.ApplicationContextRunner().withUserConfiguration(Binding.class);
        runner.run(context->{assertThat(context).hasNotFailed();assertThat(context.getBean(DefenseMaintenanceProperties.class)).isEqualTo(new DefenseMaintenanceProperties(true,Duration.ofSeconds(30),8));});
        for(String input:java.util.List.of("sma.defense.maintenance.interval=0s","sma.defense.maintenance.interval=500ms","sma.defense.maintenance.interval=61m",
                "sma.defense.maintenance.tenant-batch=0","sma.defense.maintenance.tenant-batch=101"))runner.withPropertyValues(input).run(context->assertThat(context).hasFailed());
    }
    @Test void allMaintenanceFieldsUseDistinctEnvironmentNamesAndAffectDeploymentEvidence()throws Exception{
        var environment=new StandardEnvironment();environment.getPropertySources().addFirst(new MapPropertySource("maintenance",Map.of(
                "SMA_DEFENSE_MAINTENANCE_ENABLED","false","SMA_DEFENSE_MAINTENANCE_INTERVAL","2m","SMA_DEFENSE_MAINTENANCE_TENANT_BATCH","3","SMA_DEFENSE_RETENTION","2h")));
        for(var source:new YamlPropertySourceLoader().load("application",new ClassPathResource("application.yml")))environment.getPropertySources().addLast(source);
        var value=Binder.get(environment).bind("sma.defense.maintenance",Bindable.of(DefenseMaintenanceProperties.class)).get();
        assertThat(value).isEqualTo(new DefenseMaintenanceProperties(false,Duration.ofMinutes(2),3));
        assertThat(Binder.get(environment).bind("sma.defense.storage.retention",Duration.class).get()).isEqualTo(Duration.ofHours(2));
        var challenge=new DeploymentEvidence.Challenge("a".repeat(64),"b".repeat(64),"c".repeat(64));
        var storage=new DefenseStorageProperties(null,null,null,null,null);var collection=new DefenseCollectionProperties(false,null,null,null,null,null,null,null,null,null);
        var baseline=new DefensePolicyEvidence(storage,collection,value).collect(challenge);
        for(var changed:java.util.List.of(new DefenseMaintenanceProperties(true,value.interval(),value.tenantBatch()),
                new DefenseMaintenanceProperties(false,Duration.ofMinutes(3),3),new DefenseMaintenanceProperties(false,value.interval(),4)))
            assertThat(new DefensePolicyEvidence(storage,collection,changed).collect(challenge)).isNotEqualTo(baseline);
    }
}
