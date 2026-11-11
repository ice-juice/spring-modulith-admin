package com.sma.release.deployment;

import java.time.Instant;
import java.util.List;
import com.sma.extension.protocol.*;
import com.sma.release.auth.SecurityRecords;

public final class CanaryGuard {
    private CanaryGuard() {}
    public record Request(String deploymentId,long revision,long minimumCalls,int maximumErrorBasisPoints,int maximumSlowBasisPoints,boolean automaticRollback,Instant expiresAt) {
        public Request {Checks.id(deploymentId);Checks.require(revision>0 && minimumCalls>=10 && minimumCalls<=1_000_000 && maximumErrorBasisPoints>=0 && maximumErrorBasisPoints<=10000 && maximumSlowBasisPoints>=0 && maximumSlowBasisPoints<=10000 && expiresAt!=null,"invalid-canary-guard");}
    }
    public record Binding(String id,String environment,Request request,SecurityRecords.Principal actor,String groupId,long groupRevision,long policyRevision,String releaseHash,Instant createdAt,String state,String rollbackAction) {}
    public record Sample(String id,String nodeId,String environment,long machineRevision,CanaryObservation observation,Instant receivedAt) {}
    public record Result(String state,long minute,long calls,long failures,long slowCalls,List<String> missingNodes) {}
    public static Result assess(Request guard,long minute,List<String> required,List<Sample> samples) {
        var available=samples.stream().filter(s -> s.observation().minute()==minute && s.observation().complete()).toList();
        var missing=required.stream().filter(n -> available.stream().noneMatch(s -> s.nodeId().equals(n))).toList();
        if(!missing.isEmpty())return new Result("MISSING_NODES",minute,0,0,0,missing);
        long calls=0,failures=0,slow=0;
        for(String node:required){var value=available.stream().filter(s -> s.nodeId().equals(node)).findFirst().orElseThrow().observation();calls+=value.calls();failures+=value.failures();slow+=value.slowCalls();}
        String state=calls<guard.minimumCalls()?"INSUFFICIENT_SAMPLES":failures*10000>calls*guard.maximumErrorBasisPoints() || slow*10000>calls*guard.maximumSlowBasisPoints()?"BREACHED":"HEALTHY";
        return new Result(state,minute,calls,failures,slow,List.of());
    }
}
