package com.sma.admin.common.security;

/** Host-owned authorization SPI. No business table access or business-module dependency in the rule engine. */
public interface RuleBindingPolicy {
    boolean supports(String sceneCode, String objectType);
    boolean canUse(String objectId);
}
