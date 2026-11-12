package com.sma.admin.common.internal.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import com.sma.admin.common.deployment.DeploymentEvidence;
import com.sma.admin.common.internal.cache.CacheNodeIdentity;
import com.sma.admin.common.internal.deployment.DeploymentEvidenceRegistry;
import com.sma.admin.common.internal.persistence.*;

/** Same membership rows/lock order as the node lease store. No file/network work inside these transactions. */
@Service
public class DeploymentEvidenceStore {
    public enum State { MATCH, MISMATCH, UNAVAILABLE, WAITING_BASELINE, STALE }
    public record Ticket(DeploymentEvidence.Challenge challenge,String processId,long epoch) {
        @Override public String toString(){return "DeploymentEvidenceTicket[redacted]";}
    }
    public record Verdict(State state,Instant until) { public boolean matches(){return state==State.MATCH;} }
    private record Locked(CacheNodeGroupEntity group,CacheNodeLeaseEntity lease,Instant now) { }
    private final CacheNodeGroupMapper groups;
    private final CacheNodeLeaseMapper leases;
    private final TransactionTemplate transaction;
    private final SecureRandom random=new SecureRandom();
    public DeploymentEvidenceStore(CacheNodeGroupMapper groups,CacheNodeLeaseMapper leases,PlatformTransactionManager manager){
        this.groups=groups;this.leases=leases;transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);transaction.setTimeout(3);
    }

    public Ticket challenge(CacheNodeIdentity identity,CacheNodeLeaseStore.Lease lease){
        DeploymentEvidence.requireHost(); validateLease(identity,lease);
        return transaction.execute(status->{
            var locked=lock(identity,lease.epoch());var group=locked.group();
            if(!identity.manifestHash().equals(group.getEvidenceManifestHash())||group.getEvidenceNonce()==null){
                byte[] nonce=new byte[32];random.nextBytes(nonce);String value=HexFormat.of().formatHex(nonce);
                if(groups.initializeEvidence(group.getId(),identity.manifestHash(),value)!=1)throw stale();
                return new Ticket(new DeploymentEvidence.Challenge(identity.clusterHash(),identity.manifestHash(),value),identity.processId(),lease.epoch());
            }
            return new Ticket(new DeploymentEvidence.Challenge(identity.clusterHash(),identity.manifestHash(),group.getEvidenceNonce()),identity.processId(),lease.epoch());
        });
    }

    public Verdict report(CacheNodeIdentity identity,Ticket ticket,DeploymentEvidenceRegistry.Snapshot evidence){
        DeploymentEvidence.requireHost();validateTicket(identity,ticket);Objects.requireNonNull(evidence);
        if(evidence.ready()&&!evidence.digest().matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid deployment digest");
        return transaction.execute(status->{
            var locked=lock(identity,ticket.epoch());requireChallenge(locked.group(),ticket);
            String baseline=locked.group().getEvidenceDigest();
            if(baseline==null&&evidence.ready()&&identity.properties().id().equals(identity.properties().expected().getFirst())){
                if(groups.establishEvidence(locked.group().getId(),ticket.challenge().nonce(),evidence.digest())!=1)throw stale();
                baseline=evidence.digest();
            }
            State state=!evidence.ready()?State.UNAVAILABLE:baseline==null?State.WAITING_BASELINE:
                    baseline.equals(evidence.digest())?State.MATCH:State.MISMATCH;
            var until=locked.now().plusSeconds(15);if(until.isAfter(locked.lease().getLeaseUntil()))until=locked.lease().getLeaseUntil();
            if(leases.reportEvidence(locked.lease().getId(),identity.processId(),ticket.epoch(),ticket.challenge().nonce(),
                    evidence.ready()?evidence.digest():null,state.name(),until)!=1)throw stale();
            return new Verdict(state,until);
        });
    }

    public Verdict inspect(CacheNodeIdentity identity,CacheNodeLeaseStore.Lease lease){
        DeploymentEvidence.requireHost();validateLease(identity,lease);
        return transaction.execute(status->{
            var locked=lock(identity,lease.epoch());var row=locked.lease();var group=locked.group();
            if(!identity.manifestHash().equals(group.getEvidenceManifestHash())||group.getEvidenceNonce()==null
                    ||!group.getEvidenceNonce().equals(row.getEvidenceNonce())||!identity.processId().equals(row.getEvidenceProcessId())
                    ||!Objects.equals(lease.epoch(),row.getEvidenceEpoch())||row.getEvidenceUntil()==null
                    ||!row.getEvidenceUntil().isAfter(locked.now())||row.getEvidenceState()==null)return new Verdict(State.STALE,null);
            State state=State.valueOf(row.getEvidenceState());
            if(state==State.MATCH&&(group.getEvidenceDigest()==null||!group.getEvidenceDigest().equals(row.getEvidenceDigest())))return new Verdict(State.STALE,null);
            return new Verdict(state,row.getEvidenceUntil().isBefore(row.getLeaseUntil())?row.getEvidenceUntil():row.getLeaseUntil());
        });
    }

    private Locked lock(CacheNodeIdentity identity,long epoch){
        var group=groups.selectOne(new LambdaQueryWrapper<CacheNodeGroupEntity>().eq(CacheNodeGroupEntity::getClusterHash,identity.clusterHash()).last("FOR UPDATE"));
        if(group==null||!identity.manifestHash().equals(group.getManifestHash())||identity.properties().version().longValue()!=group.getManifestVersion())throw stale();
        var row=leases.selectOne(new LambdaQueryWrapper<CacheNodeLeaseEntity>().eq(CacheNodeLeaseEntity::getClusterHash,identity.clusterHash())
                .eq(CacheNodeLeaseEntity::getNodeId,identity.properties().id()).last("FOR UPDATE"));
        var now=groups.now();
        if(row==null||!identity.processId().equals(row.getProcessId())||!identity.manifestHash().equals(row.getManifestHash())
                ||row.getEpoch()!=epoch||!row.getLeaseUntil().isAfter(now))throw stale();
        return new Locked(group,row,now);
    }
    private static void validateLease(CacheNodeIdentity identity,CacheNodeLeaseStore.Lease lease){
        if(lease==null||!identity.processId().equals(lease.processId())||!identity.properties().id().equals(lease.nodeId())||lease.epoch()<1)throw stale();
    }
    private static void validateTicket(CacheNodeIdentity identity,Ticket ticket){
        if(ticket==null||ticket.challenge()==null||!identity.processId().equals(ticket.processId())||ticket.epoch()<1
                ||!identity.clusterHash().equals(ticket.challenge().clusterHash())||!identity.manifestHash().equals(ticket.challenge().manifestHash()))throw stale();
    }
    private static void requireChallenge(CacheNodeGroupEntity group,Ticket ticket){
        if(!ticket.challenge().manifestHash().equals(group.getEvidenceManifestHash())||!ticket.challenge().nonce().equals(group.getEvidenceNonce()))throw stale();
    }
    private static IllegalStateException stale(){return new IllegalStateException("Deployment evidence identity or lease is stale");}
}
