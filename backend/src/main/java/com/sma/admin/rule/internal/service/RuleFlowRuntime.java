package com.sma.admin.rule.internal.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import com.sma.admin.common.security.CurrentUser;
import com.yomahub.liteflow.builder.el.LiteFlowChainELBuilder;
import com.yomahub.liteflow.core.FlowExecutor;
import com.yomahub.liteflow.core.NodeBooleanComponent;
import com.yomahub.liteflow.core.NodeComponent;
import com.yomahub.liteflow.core.NodeSwitchComponent;
import com.yomahub.liteflow.enums.ParseModeEnum;
import com.yomahub.liteflow.flow.FlowBus;
import com.yomahub.liteflow.property.LiteflowConfig;

/** Private immutable chains with leases: eviction never removes a chain used by an in-flight request. */
final class RuleFlowRuntime {
    private record Key(long tenant, String hash) {}
    private static final class Lease {
        final String chainId;
        int users;
        Lease(String chainId) { this.chainId = chainId; }
    }
    private record Invocation(Function<String, Object> dispatch) {}
    private static final class Holder {
        static final RuleFlowRuntime INSTANCE = new RuleFlowRuntime();
    }
    private final FlowExecutor executor;
    private final Map<Key, Lease> plans = new LinkedHashMap<>(32, .75f, true);
    private RuleFlowRuntime() {
        var config = new LiteflowConfig(); config.setParseMode(ParseModeEnum.PARSE_ALL_ON_FIRST_EXEC);
        config.setPrintExecutionLog(false); config.setChainCacheEnabled(false);
        executor = new FlowExecutor(config);
        FlowBus.addManagedNode("smaRuleActionV1", new Action());
        FlowBus.addManagedNode("smaRuleBranchV1", new Branch());
        FlowBus.addManagedNode("smaRuleSwitchV1", new Selection());
    }
    public static class Action extends NodeComponent {
        @Override public void process() { Invocation invocation = getRequestData(); invocation.dispatch().apply(getTag()); }
    }
    public static class Branch extends NodeBooleanComponent {
        @Override public boolean processBoolean() { Invocation invocation = getRequestData(); return Boolean.TRUE.equals(invocation.dispatch().apply(getTag())); }
    }
    public static class Selection extends NodeSwitchComponent {
        @Override public String processSwitch() { Invocation invocation = getRequestData(); return (String) invocation.dispatch().apply(getTag()); }
    }
    static void execute(String contentHash, RuleFlowPlan plan, Function<String, Object> dispatch) {
        var runtime = Holder.INSTANCE; var lease = runtime.acquire(contentHash, plan);
        try {
            var response = runtime.executor.execute2Resp(lease.chainId, new Invocation(dispatch));
            if (!response.isSuccess()) {
                Throwable cause = response.getCause();
                while (cause != null) {
                    if (cause instanceof RuleFailure failure) throw failure;
                    cause = cause.getCause();
                }
                throw new RuleFailure("flow", "FLOW_EXECUTION_FAILED");
            }
        } finally { runtime.release(lease); }
    }
    private synchronized Lease acquire(String hash, RuleFlowPlan plan) {
        var key = new Key(CurrentUser.tenantId(), hash); var found = plans.get(key);
        if (found == null) {
            if (plans.keySet().stream().filter(item -> item.tenant() == key.tenant()).count() >= 32) evict(key.tenant());
            if (plans.size() >= 128) evict(null);
            String chainId = "sma_rule_" + key.tenant() + "_" + hash;
            LiteFlowChainELBuilder.createChain().setChainId(chainId).setEL(plan.el).build();
            found = new Lease(chainId); plans.put(key, found);
        }
        found.users++; return found;
    }
    private synchronized void release(Lease lease) { lease.users--; }
    static int tenantSize() { return Holder.INSTANCE.sizeForTenant(); }
    private synchronized int sizeForTenant() { return (int) plans.keySet().stream().filter(key -> key.tenant() == CurrentUser.tenantId()).count(); }
    static int clearTenantIdle() { return Holder.INSTANCE.clearIdle(); }
    private synchronized int clearIdle() {
        int cleared = 0; var iterator = plans.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getKey().tenant() == CurrentUser.tenantId() && entry.getValue().users == 0) {
                FlowBus.removeChain(entry.getValue().chainId); iterator.remove(); cleared++;
            }
        }
        return cleared;
    }
    private void evict(Long tenant) {
        var iterator = plans.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().users == 0 && (tenant == null || entry.getKey().tenant() == tenant)) {
                FlowBus.removeChain(entry.getValue().chainId); iterator.remove(); return;
            }
        }
        throw new RuleFailure("flow", "PLAN_CAPACITY_BUSY");
    }
}
