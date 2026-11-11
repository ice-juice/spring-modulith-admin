package com.sma.admin.extension.internal.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.extension.ExtensionWork;
import com.sma.admin.extension.internal.persistence.*;
import com.sma.extension.protocol.*;
import com.sma.extension.protocol.ExecutionDocuments.Member;
import com.sma.extension.runtime.PluginRuntime;

@Service
public class ExtensionWorkJournal {
    private final ExtensionWorkMapper work;
    private final RuntimeReferenceMapper references;
    public ExtensionWorkJournal(ExtensionWorkMapper work,RuntimeReferenceMapper references){this.work=work;this.references=references;}
    public record Saved(long id,String deployment,String plugin,String entry,String inputHash,PluginRuntime.Selection selection,List<Member> members,
            String state,String claimId,byte[] result,String failure) {}
    public Saved find(String deployment,String plugin,String entry,String workKey) {
        var user=WorkCaller.current();
        var row=work.selectOne(new LambdaQueryWrapper<ExtensionWorkEntity>().eq(ExtensionWorkEntity::getDeploymentId,deployment)
                .eq(ExtensionWorkEntity::getPluginId,plugin).eq(ExtensionWorkEntity::getEntryId,entry).eq(ExtensionWorkEntity::getPortal,user.portal())
                .eq(ExtensionWorkEntity::getWorkKeyHash,keyHash(workKey)));
        if(row==null)return null;authorize(row);return saved(row);
    }
    @Transactional public Saved accept(String deployment,String plugin,String entry,String permission,String workKey,String inputHash,PluginRuntime.Selection selection,List<Member> members) {
        var existing=find(deployment,plugin,entry,workKey);
        if(existing!=null){Checks.require(existing.inputHash().equals(inputHash),"work-input-conflict");return existing;}
        var user=WorkCaller.current();
        var row=new ExtensionWorkEntity();row.setDeploymentId(deployment);row.setPluginId(plugin);row.setEntryId(entry);row.setPermission(permission);row.setWorkKeyHash(keyHash(workKey));
        row.setOwnerSubject(user.subject());row.setPortal(user.portal());row.setInputSha256(inputHash);
        row.setSelectionJson(new String(ProtocolJson.write(selection),StandardCharsets.UTF_8));row.setMembersJson(new String(ProtocolJson.write(members),StandardCharsets.UTF_8));row.setState("RECEIVED");
        work.insert(row);
        members.stream().map(Member::sha256).distinct().forEach(hash -> references.retain(referenceId(deployment,selection.bundleId(),hash),deployment,selection.bundleId(),hash));
        return saved(row);
    }
    @Transactional public Saved claim(long id) {
        var row=require(id);
        if(!row.getState().equals("RECEIVED"))return withoutClaim(saved(row));
        String claim=UUID.randomUUID().toString();
        int changed=work.update(new LambdaUpdateWrapper<ExtensionWorkEntity>().eq(ExtensionWorkEntity::getId,id).eq(ExtensionWorkEntity::getState,"RECEIVED")
                .set(ExtensionWorkEntity::getState,"RUNNING").set(ExtensionWorkEntity::getClaimId,claim).set(ExtensionWorkEntity::getStartedAt,Instant.now()));
        var latest=saved(require(id));
        return changed==1?latest:new Saved(latest.id(),latest.deployment(),latest.plugin(),latest.entry(),latest.inputHash(),latest.selection(),latest.members(),latest.state(),null,latest.result(),latest.failure());
    }
    @Transactional public void succeeded(long id,String claimId,byte[] result) {
        ProtocolJson.validateValue(result);var row=require(id);Checks.require(row.getState().equals("RUNNING") && claimId.equals(row.getClaimId()),"work-claim-conflict");
        Checks.require(work.update(new LambdaUpdateWrapper<ExtensionWorkEntity>().eq(ExtensionWorkEntity::getId,id).eq(ExtensionWorkEntity::getState,"RUNNING")
                .eq(ExtensionWorkEntity::getClaimId,claimId).set(ExtensionWorkEntity::getState,"SUCCEEDED")
                .set(ExtensionWorkEntity::getResultBase64,java.util.Base64.getEncoder().encodeToString(result)).set(ExtensionWorkEntity::getCompletedAt,Instant.now()))==1,"work-claim-conflict");
        for(String hash:saved(row).members().stream().map(Member::sha256).distinct().toList()) {
            Checks.require(references.update(new LambdaUpdateWrapper<RuntimeReferenceEntity>().eq(RuntimeReferenceEntity::getId,referenceId(row.getDeploymentId(),saved(row).selection().bundleId(),hash))
                    .gt(RuntimeReferenceEntity::getReferenceCount,0).setSql("reference_count=reference_count-1"))==1,"work-reference-conflict");
        }
    }
    @Transactional public void unknown(long id,String claimId,String reason) {
        Checks.text(reason,100);require(id);
        work.update(new LambdaUpdateWrapper<ExtensionWorkEntity>().eq(ExtensionWorkEntity::getId,id).eq(ExtensionWorkEntity::getState,"RUNNING")
                .eq(ExtensionWorkEntity::getClaimId,claimId).set(ExtensionWorkEntity::getState,"UNKNOWN").set(ExtensionWorkEntity::getFailure,reason));
    }
    /** No retry transition exists here. Business adapter has confirmed/fenced the exact original attempt. */
    @Transactional public Saved reconcile(long id,String expectedClaim,com.sma.admin.common.extension.ExtensionWorkReconciler.Resolution resolution) {
        var row=require(id);
        if(Set.of("SUCCEEDED","CANCELLED").contains(row.getState()))return saved(row);
        Checks.require(Set.of("RUNNING","UNKNOWN").contains(row.getState()) && java.util.Objects.equals(expectedClaim,row.getClaimId()) && expectedClaim!=null,"work-claim-conflict");
        if(resolution.outcome()==com.sma.admin.common.extension.ExtensionWorkReconciler.Outcome.PENDING)return saved(row);
        Checks.hash(resolution.evidenceSha256());String state=resolution.outcome().name();
        if(state.equals("SUCCEEDED"))ProtocolJson.validateValue(resolution.result());else Checks.require(resolution.result()==null,"cancelled-work-result-forbidden");
        int changed=work.update(new LambdaUpdateWrapper<ExtensionWorkEntity>().eq(ExtensionWorkEntity::getId,id)
                .in(ExtensionWorkEntity::getState,List.of("RUNNING","UNKNOWN")).eq(ExtensionWorkEntity::getClaimId,expectedClaim)
                .set(ExtensionWorkEntity::getState,state).set(ExtensionWorkEntity::getReconciliationEvidence,resolution.evidenceSha256())
                .set(ExtensionWorkEntity::getResultBase64,resolution.result()==null?null:java.util.Base64.getEncoder().encodeToString(resolution.result()))
                .set(ExtensionWorkEntity::getFailure,state.equals("CANCELLED")?"business-fenced-cancelled":null).set(ExtensionWorkEntity::getCompletedAt,Instant.now()));
        if(changed==0)return saved(require(id));
        for(String hash:saved(row).members().stream().map(Member::sha256).distinct().toList())
            Checks.require(references.update(new LambdaUpdateWrapper<RuntimeReferenceEntity>().eq(RuntimeReferenceEntity::getId,referenceId(row.getDeploymentId(),saved(row).selection().bundleId(),hash))
                    .gt(RuntimeReferenceEntity::getReferenceCount,0).setSql("reference_count=reference_count-1"))==1,"work-reference-conflict");
        return saved(require(id));
    }
    public Saved get(long id){return saved(require(id));}
    public ExtensionWork view(Saved saved){return new ExtensionWork(saved.id(),saved.state(),saved.selection().releaseRevision(),saved.selection().bundleId(),saved.result(),saved.failure());}
    public long references(String hash) {
        return references.selectList(new LambdaQueryWrapper<RuntimeReferenceEntity>().eq(RuntimeReferenceEntity::getArtifactHash,hash)).stream().mapToLong(RuntimeReferenceEntity::getReferenceCount).sum();
    }
    public ExecutionDocuments.Retention retained(String deployment) {
        var values=references.selectList(new LambdaQueryWrapper<RuntimeReferenceEntity>().eq(RuntimeReferenceEntity::getDeploymentId,deployment).gt(RuntimeReferenceEntity::getReferenceCount,0));
        return new ExecutionDocuments.Retention(values.stream().map(RuntimeReferenceEntity::getArtifactHash).collect(java.util.stream.Collectors.toSet()),
                values.stream().map(RuntimeReferenceEntity::getBundleId).collect(java.util.stream.Collectors.toSet()));
    }
    private ExtensionWorkEntity require(long id){var row=work.selectById(id);if(row==null)throw new ProtocolException("work-not-found");authorize(row);return row;}
    private void authorize(ExtensionWorkEntity row){var user=WorkCaller.current();if(!row.getOwnerSubject().equals(user.subject()) || !row.getPortal().equals(user.portal())
            || !user.permissions().contains(row.getPermission()))throw new AccessDeniedException("Work owner and entry permission required");}
    private Saved withoutClaim(Saved latest){return new Saved(latest.id(),latest.deployment(),latest.plugin(),latest.entry(),latest.inputHash(),latest.selection(),latest.members(),latest.state(),null,latest.result(),latest.failure());}
    private Saved saved(ExtensionWorkEntity row) {
        return new Saved(row.getId(),row.getDeploymentId(),row.getPluginId(),row.getEntryId(),row.getInputSha256(),ProtocolJson.read(row.getSelectionJson().getBytes(StandardCharsets.UTF_8),PluginRuntime.Selection.class),
                List.of(ProtocolJson.read(row.getMembersJson().getBytes(StandardCharsets.UTF_8),Member[].class)),row.getState(),row.getClaimId(),row.getResultBase64()==null?null:java.util.Base64.getDecoder().decode(row.getResultBase64()),row.getFailure());
    }
    private static String keyHash(String key){Checks.text(key,200);return Hashes.sha256(key.getBytes(StandardCharsets.UTF_8));}
    private static String referenceId(String deployment,String bundle,String hash){Checks.id(deployment);Checks.id(bundle);Checks.hash(hash);return deployment+":"+bundle+":"+hash;}
}
