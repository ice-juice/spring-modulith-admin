package com.sma.admin.common.internal.deployment;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.function.LongSupplier;
import com.sma.admin.common.deployment.DeploymentEvidence;
import com.sma.admin.common.deployment.SharedStorageWitness;
import com.sma.admin.common.internal.cache.CacheNodeIdentity;

/** One process/lease incarnation's bounded I/O. Peer reads are NOT a mutual certificate or readiness. */
public final class SharedStorageProbe {
    public record Offer(String manifestHash,String nodeId,String processId,long epoch,Map<String,String> digests){
        public Offer{
            if(manifestHash==null||!manifestHash.matches("[a-f0-9]{64}")||nodeId==null||!nodeId.matches("[a-z][a-z0-9-]{0,47}")
                    ||epoch<1||!process(processId)
                    ||digests==null||digests.isEmpty()||digests.size()>SharedStorageRegistry.MAX_RESOURCES)throw invalid();
            for(var entry:digests.entrySet())if(entry.getKey()==null||!entry.getKey().matches("[a-z]+\\.[a-z][a-z0-9.-]{0,95}")
                    ||entry.getValue()==null||!entry.getValue().matches("[a-f0-9]{64}"))throw invalid();
            digests=Collections.unmodifiableMap(new TreeMap<>(digests));
        }
        @Override public String toString(){return "SharedStorageOffer[node="+nodeId+",epoch="+epoch+",resources="+digests.size()+"]";}
        SharedStorageWitness.Scope scope(){
            return new SharedStorageWitness.Scope(hash((manifestHash+":"+epoch).getBytes(StandardCharsets.UTF_8)),nodeId,UUID.fromString(processId));
        }
    }
    private final CacheNodeIdentity identity;
    private final long epoch;
    private final SharedStorageRegistry registry;
    private final LongSupplier clock;
    private final long budget;
    private final SecureRandom random=new SecureRandom();
    private final Map<String,String> attempted=new TreeMap<>();
    private Offer own;
    public SharedStorageProbe(CacheNodeIdentity identity,long epoch,SharedStorageRegistry registry){
        this(identity,epoch,registry,System::nanoTime,Duration.ofSeconds(5));
    }
    SharedStorageProbe(CacheNodeIdentity identity,long epoch,SharedStorageRegistry registry,LongSupplier clock,Duration budget){
        if(epoch<1||budget==null||budget.isNegative()||budget.isZero()||budget.compareTo(Duration.ofSeconds(10))>0)throw invalid();
        this.identity=Objects.requireNonNull(identity);this.epoch=epoch;this.registry=Objects.requireNonNull(registry);
        this.clock=Objects.requireNonNull(clock);this.budget=budget.toNanos();
    }
    /** First attempt only; uncertain publication is resolved by reading the expected bytes, never by rewriting. */
    public synchronized Offer publish(){
        DeploymentEvidence.requireHost();long started=clock.getAsLong();var resources=registry.resources();
        for(var entry:resources.entrySet()){
            deadline(started);String id=entry.getKey();var adapter=entry.getValue();
            if(attempted.containsKey(id)){
                if(!verify(adapter,id,scope(),attempted.get(id)))throw SharedStorageRegistry.unavailable();
            }else{
                byte[] challenge=new byte[32];random.nextBytes(challenge);String digest=hash(challenge);
                attempted.put(id,digest); // Mark before I/O. An exception cannot create a new write attempt.
                try{
                    if(!digest.equals(adapter.publish(id,scope(),challenge)))throw SharedStorageRegistry.unavailable();
                }catch(RuntimeException failure){throw SharedStorageRegistry.unavailable();}
                finally{Arrays.fill(challenge,(byte)0);}
            }
            deadline(started);
        }
        own=new Offer(identity.manifestHash(),identity.properties().id(),identity.processId(),epoch,attempted);
        return own;
    }
    /** Never recreates lost probe files. Qualification consumers must revoke local usability when false. */
    public synchronized boolean verifyOwn(){
        DeploymentEvidence.requireHost();if(own==null)return false;
        return verifyOffer(own);
    }
    /** Call only with a peer offer read from the trusted, current lease store. Reverse acknowledgement is separate. */
    public synchronized boolean verifyPeer(Offer peer){
        DeploymentEvidence.requireHost();Objects.requireNonNull(peer);
        if(!identity.manifestHash().equals(peer.manifestHash())||!identity.properties().expected().contains(peer.nodeId())
                ||identity.properties().id().equals(peer.nodeId())||identity.processId().equals(peer.processId()))throw invalid();
        return verifyOffer(peer);
    }
    /** Anchor may belong to the same logical node's retired incarnation; its immutable identity comes from the store. */
    public synchronized boolean verifyAnchor(Offer anchor){
        DeploymentEvidence.requireHost();Objects.requireNonNull(anchor);
        if(!identity.manifestHash().equals(anchor.manifestHash())||!identity.properties().expected().getFirst().equals(anchor.nodeId()))throw invalid();
        return verifyOffer(anchor);
    }
    private boolean verifyOffer(Offer offer){
        long started=clock.getAsLong();var resources=registry.resources();
        if(!resources.keySet().equals(offer.digests().keySet()))throw invalid();
        try{
            for(var entry:resources.entrySet()){
                deadline(started);
                if(!verify(entry.getValue(),entry.getKey(),offer.scope(),offer.digests().get(entry.getKey())))return false;
                deadline(started);
            }
            return true;
        }catch(IllegalStateException unavailable){return false;}
    }
    private SharedStorageWitness.Scope scope(){
        return new SharedStorageWitness.Scope(hash((identity.manifestHash()+":"+epoch).getBytes(StandardCharsets.UTF_8)),
                identity.properties().id(),UUID.fromString(identity.processId()));
    }
    private static boolean verify(SharedStorageWitness adapter,String resource,SharedStorageWitness.Scope scope,String digest){
        try{return adapter.verify(resource,scope,digest);}catch(RuntimeException failure){throw SharedStorageRegistry.unavailable();}
    }
    private void deadline(long started){
        long elapsed=clock.getAsLong()-started;
        if(Thread.currentThread().isInterrupted()||elapsed<0||elapsed>=budget)throw SharedStorageRegistry.unavailable();
    }
    private static String hash(byte[] value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
        catch(NoSuchAlgorithmException failure){throw new IllegalStateException("Storage witness digest unavailable");}
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid shared storage witness binding");}
    private static boolean process(String value){
        try{return value!=null&&UUID.fromString(value).toString().equals(value);}catch(IllegalArgumentException malformed){return false;}
    }
}
