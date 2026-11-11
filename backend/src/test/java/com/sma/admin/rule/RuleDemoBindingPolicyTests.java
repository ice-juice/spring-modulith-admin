package com.sma.admin.rule;

import static org.assertj.core.api.Assertions.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.core.context.SecurityContextHolder;
import com.sma.admin.common.security.*;
import com.sma.admin.rule.internal.service.RuleDemoBindingPolicy;
import com.sma.admin.support.Users;

class RuleDemoBindingPolicyTests {
    private final ApplicationContextRunner context = new ApplicationContextRunner().withUserConfiguration(RuleDemoBindingPolicy.class);
    private SmaPrincipal actor(long tenant, String portal, Set<String> permissions) {
        return new SmaPrincipal(1001, "demo-tester", tenant, portal, null, false, DataAccess.unrestricted(), permissions, "UTC");
    }
    @Test void disabledUnlessExplicitlyEnabled() {
        context.run(app -> assertThat(app).doesNotHaveBean(RuleBindingPolicy.class));
        context.withPropertyValues("sma.rule.demo.enabled=false").run(app -> assertThat(app).doesNotHaveBean(RuleBindingPolicy.class));
        context.withPropertyValues("sma.rule.demo.enabled=true").run(app -> assertThat(app).hasSingleBean(RuleBindingPolicy.class));
    }
    @Test void allowsOnlyTheExactSyntheticSceneAndTypeAllowlist() {
        var policy = new RuleDemoBindingPolicy();
        assertThat(policy.supports("demo_rule_ratio", "RULE_DEMO")).isTrue();
        assertThat(policy.supports("demo_rule_tiers", "RULE_DEMO")).isTrue();
        assertThat(policy.supports("discount", "RULE_DEMO")).isFalse();
        assertThat(policy.supports("demo_rule_ratio_extra", "RULE_DEMO")).isFalse();
        assertThat(policy.supports("demo_rule_ratio", "PRODUCT")).isFalse();
        assertThat(policy.supports(null, "RULE_DEMO")).isFalse();
    }
    @Test void rejectsAnonymousMachineWrongPortalAndNoRulePermission() {
        var policy = new RuleDemoBindingPolicy();
        SecurityContextHolder.clearContext();
        assertThat(policy.canUse("demo:91:ratio_a")).isFalse();
        Users.as(SmaPrincipal.forApplication(91, "admin", "machine"), () -> { assertThat(policy.canUse("demo:91:ratio_a")).isFalse(); return null; });
        Users.as(actor(91, "customer", Set.of("rule:instance:edit")), () -> { assertThat(policy.canUse("demo:91:ratio_a")).isFalse(); return null; });
        Users.as(actor(91, "admin", Set.of()), () -> { assertThat(policy.canUse("demo:91:ratio_a")).isFalse(); return null; });
    }
    @Test void enforcesTrustedTenantAndExactObjectNames() {
        var policy = new RuleDemoBindingPolicy();
        Users.as(actor(91, "admin", Set.of("rule:instance:edit")), () -> {
            assertThat(policy.canUse("demo:91:ratio_a")).isTrue();
            assertThat(policy.canUse("demo:91:ratio_b")).isTrue();
            assertThat(policy.canUse("demo:92:ratio_a")).isFalse();
            assertThat(policy.canUse("demo:91:ratio_a_extra")).isFalse();
            assertThat(policy.canUse("product-1")).isFalse();
            assertThat(policy.canUse(null)).isFalse(); return null;
        });
    }
}
