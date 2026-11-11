package com.sma.admin.rule;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import com.sma.admin.common.security.*;
import com.sma.admin.rule.RuleCommands.*;
import com.sma.admin.rule.internal.service.RuleService;
import com.sma.admin.support.*;

@IntegrationTest
@TestPropertySource(properties="sma.rule.demo.enabled=true")
class RuleDemoDataTests {
    @Autowired RuleService service;
    @Test void syntheticObjectsUseNormalDraftTrialPublicationCatalogAndExecutionGates() {
        var actor = new SmaPrincipal(22001,"demo-admin",19201,"admin",null,true,DataAccess.unrestricted(),Set.of(),"UTC");
        Users.as(actor, () -> {
            var field = RuleSchema.Field.required(RuleSchema.Type.DECIMAL);
            var input = new RuleSchema(Map.of("amount",field),false);
            var params = new RuleSchema(Map.of("ratio",field),false);
            var scene = service.createScene(new Scene("demo_rule_ratio","演示比例折扣",input,field,params,List.of(RuleDefinition.Kind.EXPRESSION)));
            var example = new RuleTemplateDocument.Example("九折",Map.of("amount",100),Map.of("ratio",new BigDecimal("0.9")),new BigDecimal("90.00"));
            var document = new RuleTemplateDocument(1,input,params,field,RuleDefinition.expression("round(input.amount * params.ratio, 2)"),Map.of(),List.of(example));
            var template = service.createTemplate(new Template("demo_test_ratio","演示模板",scene.id(),document));
            var version = service.publishTemplate(template.id(),new Publish(template.revision(),"demo_publish_template")).versionId();
            assertThat(service.catalogEntries(scene.id())).anyMatch(entry -> entry.version().id()==version);
            var command = new Instance("demo_test_ratio_instance","演示实例",version,Map.of("ratio",new BigDecimal("0.9")),"RULE_DEMO","demo:19201:ratio_a","default",null,null);
            var instance = service.createInstance(command);
            assertThat(service.trial(instance.id(),new Trial(instance.revision(),Map.of("amount",100))).output()).isEqualTo(new BigDecimal("90.00"));
            service.publishInstance(instance.id(),new Publish(instance.revision(),"demo_publish_instance"));
            assertThat(service.execute(new RuleExecutionApi.Request(instance.id(),Map.of("amount",100))).output()).isEqualTo(new BigDecimal("90.00"));
            assertThatThrownBy(() -> service.createInstance(new Instance("demo_cross_tenant","拒绝跨租户",version,command.params(),"RULE_DEMO","demo:19202:ratio_a","default",null,null))).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.createInstance(new Instance("demo_real_product","拒绝真实商品",version,command.params(),"PRODUCT","demo:19201:ratio_a","default",null,null))).isInstanceOf(AccessDeniedException.class);
            return null;
        });
    }
}
