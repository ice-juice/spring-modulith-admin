package com.sma.admin.rule.internal.service;

import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.common.security.RuleBindingPolicy;

/** Synthetic rule-owned objects only. Never grants access to product/order/member data. */
@Component
@ConditionalOnProperty(prefix = "sma.rule.demo", name = "enabled", havingValue = "true")
public class RuleDemoBindingPolicy implements RuleBindingPolicy {
    private static final Set<String> SCENES = Set.of("demo_rule_ratio", "demo_rule_eligibility", "demo_rule_reduction", "demo_rule_tiers");
    private static final Set<String> OBJECTS = Set.of("ratio_a", "ratio_b", "eligibility_a", "reduction_a", "tiers_a");
    private static final Set<String> PERMISSIONS = Set.of("rule:instance:view", "rule:instance:edit", "rule:instance:publish", "rule:instance:disable", "rule:execution:run");

    @Override public boolean supports(String sceneCode, String objectType) {
        return sceneCode != null && SCENES.contains(sceneCode) && "RULE_DEMO".equals(objectType);
    }
    @Override public boolean canUse(String objectId) {
        return CurrentUser.optional().filter(actor -> !actor.machine() && "admin".equals(actor.portal())
                && (actor.superAdmin() || actor.permissions().stream().anyMatch(PERMISSIONS::contains)))
                .map(actor -> OBJECTS.stream().anyMatch(key -> ("demo:" + actor.tenantId() + ":" + key).equals(objectId)))
                .orElse(false);
    }
}
