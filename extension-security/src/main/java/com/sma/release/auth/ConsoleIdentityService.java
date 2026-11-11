package com.sma.release.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import com.sma.extension.protocol.*;
import com.sma.release.auth.SecurityRecords.*;
import com.sma.release.crypto.SecretVault;
import com.sma.release.store.ReleaseDatabase;

/** A trusted host attests identity, never possession of the independently held operation factors. */
@Service
public class ConsoleIdentityService {
    private final ReleaseDatabase database;
    private final MachineService machines;
    private final CredentialService credentials;
    private final SecretVault vault;
    private final IdentityService identities;
    public ConsoleIdentityService(ReleaseDatabase database,MachineService machines,CredentialService credentials,SecretVault vault,IdentityService identities) {
        this.database=database;this.machines=machines;this.credentials=credentials;this.vault=vault;this.identities=identities;
    }
    public record Subject(String tenantId,String portal,String hostUserId) {
        public Subject {
            Checks.require(tenantId!=null && tenantId.matches("[1-9][0-9]{0,18}") && hostUserId!=null && hostUserId.matches("[1-9][0-9]{0,18}"),"invalid-console-subject");
            Checks.id(portal);
        }
    }
    public record Exchange(Subject subject,String hostSessionHash) {
        public Exchange { Checks.require(subject!=null,"invalid-console-subject");Checks.hash(hostSessionHash); }
    }
    public record Binding(String id,String machineId,Subject subject,String userId,long revision,boolean enabled) {}
    public record Bind(String machineId,Subject subject,String userId,long expectedRevision,boolean enabled) {}
    public record Origin(String sessionId,String bindingId,long bindingRevision,String machineId,long machineRevision) {}
    public record Ticket(String id,String tokenHash,String sealedToken,Instant expiresAt) {}
    public record Status(boolean enrolled) {}

    public static String bindingId(String machineId,Subject subject) {
        Checks.id(machineId);Checks.require(subject!=null,"invalid-console-subject");
        return "b-"+Hashes.sha256(ProtocolJson.write(List.of(machineId,subject.tenantId(),subject.portal(),subject.hostUserId())));
    }
    public String bind(ReleaseDatabase.Tx tx,Principal actor,Bind request) {
        Checks.require(actor.securityAdmin(),"forbidden");
        return bindLocal(tx,actor.userId(),request);
    }
    /** Only called by the protected local operator bootstrap or an already consumed MFA command. */
    public String bindLocal(ReleaseDatabase.Tx tx,String actor,Bind request) {
        String id=bindingId(request.machineId(),request.subject());
        var machine=tx.get("machine",request.machineId(),MachineService.Machine.class).orElseThrow(()->new ProtocolException("machine-not-found"));
        Checks.require(machine.kind()==MachineService.Kind.CONSOLE && machine.enabled() && machine.environments().contains("security"),"console-machine-required");
        Checks.require(tx.get("user",request.userId(),User.class).isPresent(),"user-not-found");
        var old=tx.get("console-binding",id,Binding.class);
        Checks.require(old.map(Binding::revision).orElse(0L)==request.expectedRevision(),"binding-revision-conflict");
        // One release identity must not silently bridge multiple tenants or natural persons.
        Checks.require(!request.enabled() || tx.list("console-binding",Binding.class).stream().noneMatch(b->b.enabled() && !b.id().equals(id) && b.userId().equals(request.userId())),"release-identity-already-bound");
        tx.put("console-binding",id,new Binding(id,request.machineId(),request.subject(),request.userId(),request.expectedRevision()+1,request.enabled()));
        tx.audit(actor,"console-identity-bind",id,"updated",Hashes.sha256(ProtocolJson.write(request)));return id;
    }
    public LoginTicket exchange(String machineToken,Exchange input) {
        return database.transaction(tx->{
            var machine=machines.authenticate(tx,machineToken,MachineService.Kind.CONSOLE,"security");
            var binding=tx.get("console-binding",bindingId(machine.id(),input.subject()),Binding.class).orElseThrow(()->new ProtocolException("console-identity-unbound"));
            Checks.require(binding.enabled(),"console-identity-disabled");
            var user=tx.get("user",binding.userId(),User.class).orElseThrow(()->new ProtocolException("unauthenticated"));
            Checks.require(user.enabled(),"unauthenticated");
            String index="c-"+Hashes.sha256(ProtocolJson.write(List.of(binding.id(),input.hostSessionHash())));
            var previous=tx.get("console-ticket",index,Ticket.class);
            if(previous.isPresent()) {
                var ticket=previous.get();var session=tx.get("session",ticket.tokenHash(),Session.class);
                if(session.isPresent()) {
                    var s=session.get();var origin=tx.get("console-origin",s.id(),Origin.class);
                    if(origin.isPresent() && matches(origin.get(),binding,machine) && s.credentialVersion()==user.credentialVersion()
                            && tx.now().isBefore(s.expiresAt()) && tx.now().isBefore(s.createdAt().plusSeconds(3600))) {
                        var expires=tx.now().plusSeconds(90);
                        tx.put("session",s.tokenHash(),new Session(s.tokenHash(),s.id(),s.userId(),s.credentialVersion(),s.createdAt(),expires));
                        tx.put("console-ticket",index,new Ticket(index,s.tokenHash(),ticket.sealedToken(),expires));
                        return new LoginTicket(new String(vault.open(index,ticket.sealedToken()),StandardCharsets.UTF_8),expires);
                    }
                    tx.delete("session",s.tokenHash());tx.delete("console-origin",s.id());
                }
                tx.delete("console-ticket",index);
            }
            Checks.require(tx.list("session",Session.class).stream().filter(s->s.userId().equals(user.id()) && tx.now().isBefore(s.expiresAt())).count()<20,"too-many-console-sessions");
            String token=credentials.randomToken(),hash=credentials.tokenHash(token),id=UUID.randomUUID().toString();var expires=tx.now().plusSeconds(90);
            tx.put("session",hash,new Session(hash,id,user.id(),user.credentialVersion(),tx.now(),expires));
            tx.put("console-origin",id,new Origin(id,binding.id(),binding.revision(),machine.id(),machine.revision()));
            tx.put("console-ticket",index,new Ticket(index,hash,vault.seal(index,token.getBytes(StandardCharsets.UTF_8)),expires));
            tx.audit(user.id(),"console-session",binding.id(),"identity-only",Hashes.sha256(new byte[0]));return new LoginTicket(token,expires);
        });
    }
    public static boolean validateOrigin(ReleaseDatabase.Tx tx,Session session) {
        var origin=tx.get("console-origin",session.id(),Origin.class);
        if(origin.isEmpty())return false;
        var binding=tx.get("console-binding",origin.get().bindingId(),Binding.class).orElseThrow(()->new ProtocolException("unauthenticated"));
        var machine=tx.get("machine",origin.get().machineId(),MachineService.Machine.class).orElseThrow(()->new ProtocolException("unauthenticated"));
        Checks.require(binding.userId().equals(session.userId()) && matches(origin.get(),binding,machine),"unauthenticated");return true;
    }
    public static boolean originActive(ReleaseDatabase.Tx tx,Session session) {
        try { validateOrigin(tx,session);return true; } catch(ProtocolException revoked){return false;}
    }
    private static boolean matches(Origin origin,Binding binding,MachineService.Machine machine) {
        return binding.enabled() && binding.revision()==origin.bindingRevision() && machine.enabled() && machine.kind()==MachineService.Kind.CONSOLE
                && machine.environments().contains("security") && machine.revision()==origin.machineRevision();
    }
    public Status status(String session) {return database.transaction(tx->{var p=identities.principal(tx,session);return new Status(tx.get("user",p.userId(),User.class).orElseThrow().enrolled());});}
    public List<Binding> bindings(String session,int offset,int limit) {
        Checks.require(offset>=0 && offset<=100000 && limit>0 && limit<=200,"invalid-page");
        return database.transaction(tx->{Checks.require(identities.principal(tx,session).securityAdmin(),"forbidden");return tx.list("console-binding",Binding.class).stream().skip(offset).limit(limit).toList();});
    }
}
