package com.sma.admin.common.internal.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.common.deployment.DeploymentEvidence;
import com.sma.admin.common.internal.cache.CacheNodeIdentity;
import com.sma.admin.common.internal.deployment.SharedStorageProbe.Offer;
import com.sma.admin.common.internal.persistence.*;

/** Host-only bounded witness exchange. Filesystem I/O and permission-bearing business work never run here. */
@Service
public class SharedStorageStore {
    public record Read(String nodeId,String processId,long epoch,long generation){
        public Read{
            if(nodeId==null||!nodeId.matches("[a-z][a-z0-9-]{0,47}")||processId==null||!processId.matches("[a-f0-9-]{36}")||epoch<1||generation<1)throw unavailable();
        }
        static Read of(Report report){return new Read(report.offer().nodeId(),report.offer().processId(),report.offer().epoch(),report.generation());}
    }
    public record Report(Offer offer,long generation,boolean healthy,boolean certified,String anchorDigest,List<Read> reads,Instant until){
        public Report{
            Objects.requireNonNull(offer);
            if(generation<1||reads==null||reads.size()>31||new HashSet<>(reads).size()!=reads.size()
                    ||anchorDigest!=null&&!anchorDigest.matches("[a-f0-9]{64}")||certified&&(!healthy||anchorDigest==null||until==null))throw unavailable();
            reads=List.copyOf(reads);
        }
        public Read reference(){return Read.of(this);}
        @Override public String toString(){return "StorageReport[healthy="+healthy+",certified="+certified+"]";}
    }
    public record View(Offer anchor,String anchorDigest,Report self,List<Report> peers){
        public View{peers=List.copyOf(peers);}
        @Override public String toString(){return "StorageView[peers="+peers.size()+"]";}
    }
    private record Locked(CacheNodeGroupEntity group,CacheNodeLeaseEntity self,List<CacheNodeLeaseEntity> nodes,Instant now){}
    private final CacheNodeGroupMapper groups;private final CacheNodeLeaseMapper leases;private final JsonMapper json;private final TransactionTemplate transaction;
    public SharedStorageStore(CacheNodeGroupMapper groups,CacheNodeLeaseMapper leases,JsonMapper json,PlatformTransactionManager manager){
        this.groups=groups;this.leases=leases;this.json=json;transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);transaction.setTimeout(3);
    }
    /** Register bytes already published once by this incarnation; never accepts a replacement offer in that incarnation. */
    public View register(CacheNodeIdentity identity,CacheNodeLeaseStore.Lease lease,Offer offer){
        DeploymentEvidence.requireHost();binding(identity,lease,offer);
        return transaction.execute(status->{
            var locked=lock(identity,lease);var anchor=anchor(identity,locked.group());
            if(anchor!=null&&!anchor.digests().keySet().equals(offer.digests().keySet()))throw unavailable();
            var report=current(locked.self(),identity);
            if(report!=null&&!report.offer().equals(offer))throw unavailable();
            if(report==null){report=new Report(offer,1,false,false,null,List.of(),null);save(locked.self(),report);}
            if(anchor==null&&identity.properties().id().equals(identity.properties().expected().getFirst())){
                if(groups.initializeStorage(locked.group().getId(),identity.manifestHash(),encode(offer))!=1)throw unavailable();anchor=offer;
            }
            return view(identity,locked,anchor,report);
        });
    }
    public View snapshot(CacheNodeIdentity identity,CacheNodeLeaseStore.Lease lease){
        DeploymentEvidence.requireHost();binding(identity,lease,null);
        return transaction.execute(status->{var locked=lock(identity,lease);return view(identity,locked,anchor(identity,locked.group()),current(locked.self(),identity));});
    }
    /** Observations must come from I/O after snapshot; stale generations never recreate an earlier certificate. */
    public Report observe(CacheNodeIdentity identity,CacheNodeLeaseStore.Lease lease,long generation,String anchorDigest,
                          boolean ownValid,boolean anchorValid,List<Read> reads){
        DeploymentEvidence.requireHost();binding(identity,lease,null);
        if(reads==null||reads.size()>31||new HashSet<>(reads).size()!=reads.size())throw unavailable();
        return transaction.execute(status->{
            var locked=lock(identity,lease);var report=current(locked.self(),identity);var anchor=anchor(identity,locked.group());
            if(report==null||report.generation()!=generation)throw unavailable();
            boolean valid=ownValid&&anchorValid&&anchor!=null&&Objects.equals(anchorDigest,digest(anchor));
            boolean gap=report.until()!=null&&!report.until().isAfter(locked.now());
            long nextGeneration=gap||report.healthy()&&!valid?Math.addExact(report.generation(),1):report.generation();
            var accepted=new ArrayList<Read>();boolean mutual=false;
            if(valid){
                for(var row:locked.nodes()){
                    if(row.getNodeId().equals(identity.properties().id())||!configurationValid(row,locked.group(),locked.now()))continue;
                    var peer=current(row,identity);if(peer==null||!peer.offer().digests().keySet().equals(report.offer().digests().keySet()))continue;
                    var reference=peer.reference();if(!reads.contains(reference))continue;accepted.add(reference);
                    var reverse=new Read(identity.properties().id(),identity.processId(),lease.epoch(),nextGeneration);
                    if(peer.healthy()&&peer.until()!=null&&peer.until().isAfter(locked.now())&&Objects.equals(peer.anchorDigest(),anchorDigest)
                            &&peer.reads().contains(reverse))mutual=true;
                }
            }
            boolean certified=valid&&(mutual||!gap&&report.certified()&&report.generation()==nextGeneration&&Objects.equals(report.anchorDigest(),anchorDigest));
            var until=locked.now().plusSeconds(15);if(until.isAfter(locked.self().getLeaseUntil()))until=locked.self().getLeaseUntil();
            var next=new Report(report.offer(),nextGeneration,valid,certified,valid?anchorDigest:null,accepted,until);
            save(locked.self(),next);return next;
        });
    }
    private View view(CacheNodeIdentity identity,Locked locked,Offer anchor,Report report){
        var peers=new ArrayList<Report>();
        for(var row:locked.nodes())if(!row.getNodeId().equals(identity.properties().id())&&configurationValid(row,locked.group(),locked.now())){
            var peer=current(row,identity);if(peer!=null)peers.add(peer);
        }
        return new View(anchor,anchor==null?null:digest(anchor),report,peers);
    }
    private Locked lock(CacheNodeIdentity identity,CacheNodeLeaseStore.Lease lease){
        var group=groups.selectOne(new LambdaQueryWrapper<CacheNodeGroupEntity>().eq(CacheNodeGroupEntity::getClusterHash,identity.clusterHash()).last("FOR UPDATE"));
        if(group==null||!identity.manifestHash().equals(group.getManifestHash())||identity.properties().version().longValue()!=group.getManifestVersion())throw unavailable();
        var nodes=leases.selectList(new LambdaQueryWrapper<CacheNodeLeaseEntity>().eq(CacheNodeLeaseEntity::getClusterHash,identity.clusterHash())
                .in(CacheNodeLeaseEntity::getNodeId,identity.properties().expected()).orderByAsc(CacheNodeLeaseEntity::getNodeId).last("FOR UPDATE"));
        var self=nodes.stream().filter(row->row.getNodeId().equals(identity.properties().id())).findFirst().orElseThrow(SharedStorageStore::unavailable);
        var now=groups.now();
        if(!self.getProcessId().equals(identity.processId())||self.getEpoch()!=lease.epoch()||!configurationValid(self,group,now))throw unavailable();
        return new Locked(group,self,nodes,now);
    }
    private static boolean configurationValid(CacheNodeLeaseEntity row,CacheNodeGroupEntity group,Instant now){
        return row.getManifestHash().equals(group.getManifestHash())&&row.getLeaseUntil().isAfter(now)&&"MATCH".equals(row.getEvidenceState())
                &&row.getProcessId().equals(row.getEvidenceProcessId())&&Objects.equals(row.getEpoch(),row.getEvidenceEpoch())
                &&group.getManifestHash().equals(group.getEvidenceManifestHash())&&group.getEvidenceNonce()!=null&&group.getEvidenceNonce().equals(row.getEvidenceNonce())
                &&group.getEvidenceDigest()!=null&&group.getEvidenceDigest().equals(row.getEvidenceDigest())&&row.getEvidenceUntil()!=null&&row.getEvidenceUntil().isAfter(now);
    }
    private Report current(CacheNodeLeaseEntity row,CacheNodeIdentity identity){
        if(row.getStorageReportJson()==null)return null;var report=decode(row.getStorageReportJson(),Report.class);
        var offer=report.offer();
        if(!offer.manifestHash().equals(identity.manifestHash())||!offer.nodeId().equals(row.getNodeId())||!offer.processId().equals(row.getProcessId())||offer.epoch()!=row.getEpoch())return null;
        return report;
    }
    private Offer anchor(CacheNodeIdentity identity,CacheNodeGroupEntity group){
        if(!identity.manifestHash().equals(group.getStorageManifestHash())||group.getStorageAnchorJson()==null)return null;
        var anchor=decode(group.getStorageAnchorJson(),Offer.class);
        if(!anchor.manifestHash().equals(identity.manifestHash())||!anchor.nodeId().equals(identity.properties().expected().getFirst()))throw unavailable();return anchor;
    }
    private void save(CacheNodeLeaseEntity row,Report report){
        if(leases.reportStorage(row.getId(),row.getProcessId(),row.getEpoch(),encode(report))!=1)throw unavailable();
    }
    private String encode(Object value){
        try{String encoded=json.writeValueAsString(value);if(encoded.getBytes(StandardCharsets.UTF_8).length>32768)throw unavailable();return encoded;}
        catch(RuntimeException failure){throw unavailable();}
    }
    private <T>T decode(String value,Class<T> type){
        try{if(value.getBytes(StandardCharsets.UTF_8).length>32768)throw unavailable();return json.readValue(value,type);}
        catch(RuntimeException failure){throw unavailable();}
    }
    private String digest(Offer offer){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode(offer).getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException failure){throw unavailable();}
    }
    private static void binding(CacheNodeIdentity identity,CacheNodeLeaseStore.Lease lease,Offer offer){
        if(lease==null||!identity.processId().equals(lease.processId())||!identity.properties().id().equals(lease.nodeId())||lease.epoch()<1)throw unavailable();
        if(offer!=null&&(!offer.manifestHash().equals(identity.manifestHash())||!offer.nodeId().equals(identity.properties().id())
                ||!offer.processId().equals(identity.processId())||offer.epoch()!=lease.epoch()))throw unavailable();
    }
    private static IllegalStateException unavailable(){return new IllegalStateException("Shared storage evidence unavailable or stale");}
}
