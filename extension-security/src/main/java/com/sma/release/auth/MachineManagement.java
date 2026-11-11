package com.sma.release.auth;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import com.sma.extension.protocol.*;
import com.sma.release.auth.SecurityRecords.Principal;
import com.sma.release.crypto.SecretVault;
import com.sma.release.store.ReleaseDatabase;

/** Human step-up commands manage workload identities; no workload can provision another workload. */
@Service
public class MachineManagement {
    private final ReleaseDatabase database;
    private final IdentityService identities;
    private final CredentialService credentials;
    private final SecretVault vault;
    public MachineManagement(ReleaseDatabase database,IdentityService identities,CredentialService credentials,SecretVault vault) {
        this.database=database;this.identities=identities;this.credentials=credentials;this.vault=vault;
    }
    public record Create(String id,MachineService.Kind kind,Set<String> environments) {}
    public record Change(String id,long expectedRevision,Set<String> environments,boolean enabled) {}
    public record Rotate(String id,long expectedRevision) {}
    public record Activate(String id,long expectedRevision,String rotationId) {}
    public record View(String id,MachineService.Kind kind,Set<String> environments,boolean enabled,long revision,String rotationId,Instant rotationExpiresAt) {}
    public record TokenDelivery(String id,long revision,String token,Instant expiresAt) {
        @Override public String toString(){return "TokenDelivery[redacted]";}
    }
    private record Pending(String id,String tokenHash,long revision,Instant expiresAt) {}
    private record Delivery(String actorId,String sessionId,long credentialVersion,long revision,String sealed,Instant expiresAt) {}
    public String create(ReleaseDatabase.Tx tx,Principal actor,Create request) {
        admin(actor);Checks.id(request.id());Checks.require(tx.get("machine",request.id(),MachineService.Machine.class).isEmpty(),"machine-already-exists");
        String token=credentials.randomToken();
        var machine=new MachineService.Machine(request.id(),request.kind(),credentials.tokenHash(token),request.environments(),true,1);
        tx.put("machine",machine.id(),machine);deliver(tx,actor,machine,token);
        audit(tx,actor,machine,"machine-create");return machine.id();
    }
    public String change(ReleaseDatabase.Tx tx,Principal actor,Change request) {
        admin(actor);var old=expected(tx,request.id(),request.expectedRevision());
        var changed=new MachineService.Machine(old.id(),old.kind(),old.tokenHash(),request.environments(),request.enabled(),old.revision()+1);
        tx.put("machine",old.id(),changed);clear(tx,old.id());audit(tx,actor,changed,"machine-update");return old.id();
    }
    public String stage(ReleaseDatabase.Tx tx,Principal actor,Rotate request) {
        admin(actor);var machine=expected(tx,request.id(),request.expectedRevision());
        String token=credentials.randomToken();var expires=tx.now().plusSeconds(900);
        tx.put("machine-rotation",machine.id(),new Pending("rotation-"+java.util.UUID.randomUUID(),credentials.tokenHash(token),machine.revision(),expires));
        deliver(tx,actor,machine,token);audit(tx,actor,machine,"machine-rotation-stage");return machine.id();
    }
    public String activate(ReleaseDatabase.Tx tx,Principal actor,Activate request) {
        admin(actor);var machine=expected(tx,request.id(),request.expectedRevision());
        var pending=tx.get("machine-rotation",machine.id(),Pending.class).orElseThrow(() -> new ProtocolException("machine-rotation-unavailable"));
        Checks.require(pending.id().equals(request.rotationId()),"machine-rotation-context-mismatch");
        Checks.require(pending.revision()==machine.revision() && tx.now().isBefore(pending.expiresAt()),"machine-rotation-expired");
        Checks.require(tx.get("machine-delivery",machine.id(),Delivery.class).isEmpty(),"machine-token-not-delivered");
        var updated=new MachineService.Machine(machine.id(),machine.kind(),pending.tokenHash(),machine.environments(),machine.enabled(),machine.revision()+1);
        tx.put("machine",machine.id(),updated);clear(tx,machine.id());audit(tx,actor,updated,"machine-rotation-activate");return machine.id();
    }
    public List<View> list(String session,int offset,int limit) {
        Checks.require(offset>=0 && offset<=100000 && limit>0 && limit<=200,"invalid-page");
        return database.transaction(tx -> {admin(identities.principal(tx,session));return tx.list("machine",MachineService.Machine.class).stream()
                .sorted(java.util.Comparator.comparing(MachineService.Machine::id)).skip(offset).limit(limit)
                .map(m -> new View(m.id(),m.kind(),m.environments(),m.enabled(),m.revision(),tx.get("machine-rotation",m.id(),Pending.class).map(Pending::id).orElse(null),tx.get("machine-rotation",m.id(),Pending.class).map(Pending::expiresAt).orElse(null))).toList();});
    }
    public TokenDelivery delivery(String session,String id) {
        return database.transaction(tx -> {
            var actor=identities.principal(tx,session);admin(actor);var machine=required(tx,id);
            var value=tx.get("machine-delivery",id,Delivery.class).orElseThrow(() -> new ProtocolException("machine-token-unavailable"));
            Checks.require(value.actorId().equals(actor.userId()) && value.sessionId().equals(actor.sessionId()) && value.credentialVersion()==actor.credentialVersion()
                    && value.revision()==machine.revision() && tx.now().isBefore(value.expiresAt()),"machine-token-unavailable");
            String token=new String(vault.open("machine-delivery:"+id,value.sealed()),java.nio.charset.StandardCharsets.UTF_8);
            tx.delete("machine-delivery",id);audit(tx,actor,machine,"machine-token-deliver");return new TokenDelivery(id,machine.revision(),token,value.expiresAt());
        });
    }
    private void deliver(ReleaseDatabase.Tx tx,Principal actor,MachineService.Machine machine,String token) {
        tx.put("machine-delivery",machine.id(),new Delivery(actor.userId(),actor.sessionId(),actor.credentialVersion(),machine.revision(),
                vault.seal("machine-delivery:"+machine.id(),token.getBytes(java.nio.charset.StandardCharsets.UTF_8)),tx.now().plusSeconds(900)));
    }
    private static void clear(ReleaseDatabase.Tx tx,String id){tx.delete("machine-rotation",id);tx.delete("machine-delivery",id);}
    private static void audit(ReleaseDatabase.Tx tx,Principal actor,MachineService.Machine machine,String action) {
        // Never write bearer values or token hashes into the audit stream.
        tx.audit(actor.userId(),action,machine.id(),"success",Hashes.sha256(ProtocolJson.write(new View(machine.id(),machine.kind(),machine.environments(),machine.enabled(),machine.revision(),null,null))));
    }
    private static MachineService.Machine required(ReleaseDatabase.Tx tx,String id){Checks.id(id);return tx.get("machine",id,MachineService.Machine.class).orElseThrow(() -> new ProtocolException("machine-not-found"));}
    private static MachineService.Machine expected(ReleaseDatabase.Tx tx,String id,long revision){var m=required(tx,id);Checks.require(m.revision()==revision,"machine-revision-conflict");return m;}
    private static void admin(Principal actor){Checks.require(actor.securityAdmin(),"forbidden");}
}
