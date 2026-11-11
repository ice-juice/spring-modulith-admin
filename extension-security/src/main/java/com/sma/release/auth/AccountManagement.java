package com.sma.release.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import com.sma.extension.protocol.*;
import com.sma.release.auth.SecurityRecords.*;
import com.sma.release.crypto.SecretVault;
import com.sma.release.store.ReleaseDatabase;

/** Independent security identities; business-database super-admin flags cannot grant these privileges. */
@Service
public class AccountManagement {
    private final ReleaseDatabase database;private final IdentityService identities;private final CredentialService credentials;private final SecretVault vault;
    public AccountManagement(ReleaseDatabase database,IdentityService identities,CredentialService credentials,SecretVault vault) {
        this.database=database;this.identities=identities;this.credentials=credentials;this.vault=vault;
    }
    public record Create(String username,String loginPassword,String operationPassword,boolean securityAdmin) {
        @Override public String toString(){return "Create[redacted]";}
    }
    public record Change(String userId,long expectedCredentialVersion,boolean enabled,boolean securityAdmin) {}
    public record OperationPassword(String newOperationPassword) {
        @Override public String toString(){return "OperationPassword[redacted]";}
    }
    public record Reset(String userId,long expectedCredentialVersion,String newOperationPassword) {
        public Reset(String userId,long expectedCredentialVersion){this(userId,expectedCredentialVersion,null);}
        @Override public String toString(){return "Reset[redacted]";}
    }
    public record PasswordChange(String currentLoginPassword,String newLoginPassword,String newOperationPassword) {
        @Override public String toString(){return "PasswordChange[redacted]";}
    }
    public record Account(String id,String username,boolean enabled,boolean enrolled,boolean securityAdmin,long credentialVersion) {}
    public record Notice(String id,String userId,String action,String actorId,Instant occurredAt) {}
    private record Delivery(String actorId,String sessionId,String targetUserId,long targetCredentialVersion,String sealedTicket,Instant expiresAt) {}
    public String create(ReleaseDatabase.Tx tx,Principal actor,Create request) {
        requireAdmin(actor);
        var ticket=identities.createAccount(tx,actor.userId(),request.username(),request.loginPassword()==null?credentials.randomToken():request.loginPassword(),request.operationPassword(),request.securityAdmin());
        var user=tx.get("user",ticket.userId(),User.class).orElseThrow();storeDelivery(tx,actor,user,ticket);notice(tx,user.id(),actor.userId(),"account-created");return user.id();
    }
    public String change(ReleaseDatabase.Tx tx,Principal actor,Change request) {
        requireAdmin(actor);var user=requireUser(tx,request.userId());Checks.require(user.credentialVersion()==request.expectedCredentialVersion(),"credential-version-conflict");
        if(user.securityAdmin() && (!request.enabled() || !request.securityAdmin())) {
            Checks.require(tx.list("user",User.class).stream().anyMatch(u -> !u.id().equals(user.id()) && u.securityAdmin() && u.enabled() && u.enrolled()),"last-security-admin-protected");
        }
        var changed=new User(user.id(),user.username(),user.loginHash(),user.operationHash(),user.sealedTotp(),user.usedTotpStep(),user.credentialVersion()+1,
                request.enabled(),user.enrolled(),request.securityAdmin(),user.enrollmentTokenHash(),user.enrollmentExpiresAt(),user.recoveryHashes());
        tx.put("user",user.id(),changed);tx.delete("enrollment-delivery",user.id());notice(tx,user.id(),actor.userId(),"account-permissions-changed");return user.id();
    }
    public String resetFactor(ReleaseDatabase.Tx tx,Principal actor,Reset request) {
        requireAdmin(actor);var user=requireUser(tx,request.userId());Checks.require(user.credentialVersion()==request.expectedCredentialVersion(),"credential-version-conflict");
        Checks.require(!user.id().equals(actor.userId()),"use-self-recovery-for-own-factor");
        String operationHash=user.operationHash();
        if(request.newOperationPassword()!=null) {
            Checks.require(!credentials.passwordMatches(request.newOperationPassword(),user.loginHash()),"operation-password-must-differ");
            operationHash=credentials.passwordHash(request.newOperationPassword());
        }
        String token=credentials.randomToken();var expires=tx.now().plusSeconds(900);
        var changed=new User(user.id(),user.username(),user.loginHash(),operationHash,credentials.newSealedTotp(user.id()),-1,user.credentialVersion()+1,
                user.enabled(),false,user.securityAdmin(),credentials.tokenHash(token),expires,List.of());
        tx.put("user",user.id(),changed);tx.delete("enrollment-shown",user.id());
        storeDelivery(tx,actor,changed,new NewAccount(user.id(),token,expires));notice(tx,user.id(),actor.userId(),"factor-reset-sessions-revoked");return user.id();
    }
    public String changeCredentials(ReleaseDatabase.Tx tx,Principal actor,PasswordChange request) {
        var user=requireUser(tx,actor.userId());
        Checks.require(credentials.passwordMatches(request.currentLoginPassword(),user.loginHash()),"authentication-failed");
        boolean login=request.newLoginPassword()!=null,operation=request.newOperationPassword()!=null;
        Checks.require(login || operation,"credential-change-required");
        String newLogin=login?credentials.passwordHash(request.newLoginPassword()):user.loginHash();
        String newOperation=operation?credentials.passwordHash(request.newOperationPassword()):user.operationHash();
        Checks.require(!(login && credentials.passwordMatches(request.newLoginPassword(),newOperation))
                && !(operation && credentials.passwordMatches(request.newOperationPassword(),newLogin)),"operation-password-must-differ");
        tx.put("user",user.id(),new User(user.id(),user.username(),newLogin,newOperation,user.sealedTotp(),user.usedTotpStep(),user.credentialVersion()+1,
                user.enabled(),user.enrolled(),user.securityAdmin(),user.enrollmentTokenHash(),user.enrollmentExpiresAt(),user.recoveryHashes()));
        notice(tx,user.id(),actor.userId(),"credentials-changed-sessions-revoked");return user.id();
    }
    /** Exact operation-password change has already consumed the old password + OTP authorization. */
    public String changeOperationPassword(ReleaseDatabase.Tx tx,Principal actor,OperationPassword request) {
        var user=requireUser(tx,actor.userId());
        Checks.require(!credentials.passwordMatches(request.newOperationPassword(),user.loginHash()),"operation-password-must-differ");
        String hash=credentials.passwordHash(request.newOperationPassword());
        tx.put("user",user.id(),new User(user.id(),user.username(),user.loginHash(),hash,user.sealedTotp(),user.usedTotpStep(),user.credentialVersion()+1,
                user.enabled(),user.enrolled(),user.securityAdmin(),user.enrollmentTokenHash(),user.enrollmentExpiresAt(),user.recoveryHashes()));
        notice(tx,user.id(),actor.userId(),"operation-password-changed-sessions-revoked");return user.id();
    }
    public List<Account> accounts(String session,int offset,int limit) {
        bounds(offset,limit);
        return database.transaction(tx -> {requireAdmin(identities.principal(tx,session));return tx.list("user",User.class).stream().skip(offset).limit(limit)
                .map(u -> new Account(u.id(),u.username(),u.enabled(),u.enrolled(),u.securityAdmin(),u.credentialVersion())).toList();});
    }
    public NewAccount delivery(String session,String userId) {
        return database.transaction(tx -> {
            var actor=identities.principal(tx,session);requireAdmin(actor);
            var value=tx.get("enrollment-delivery",userId,Delivery.class).orElseThrow(() -> new ProtocolException("enrollment-ticket-unavailable"));
            Checks.require(value.actorId().equals(actor.userId()) && value.sessionId().equals(actor.sessionId()) && tx.now().isBefore(value.expiresAt()),"enrollment-ticket-unavailable");
            var user=requireUser(tx,userId);Checks.require(!user.enrolled() && user.credentialVersion()==value.targetCredentialVersion(),"enrollment-ticket-unavailable");
            var ticket=ProtocolJson.read(vault.open("enrollment-delivery:"+userId,value.sealedTicket()),NewAccount.class);
            tx.delete("enrollment-delivery",userId);tx.audit(actor.userId(),"enrollment-ticket-deliver",userId,"delivered-once",Hashes.sha256(new byte[0]));return ticket;
        });
    }
    public List<Notice> notices(String session,int offset,int limit) {
        bounds(offset,limit);return database.transaction(tx -> {var actor=identities.principal(tx,session);return tx.list("security-notice",Notice.class).stream()
                .filter(n -> n.userId().equals(actor.userId())).sorted(java.util.Comparator.comparing(Notice::occurredAt).reversed()).skip(offset).limit(limit).toList();});
    }
    private void storeDelivery(ReleaseDatabase.Tx tx,Principal actor,User user,NewAccount ticket) {
        tx.put("enrollment-delivery",user.id(),new Delivery(actor.userId(),actor.sessionId(),user.id(),user.credentialVersion(),
                vault.seal("enrollment-delivery:"+user.id(),ProtocolJson.write(ticket)),ticket.expiresAt()));
    }
    private void notice(ReleaseDatabase.Tx tx,String user,String actor,String action){String id="notice-"+UUID.randomUUID();tx.put("security-notice",id,new Notice(id,user,action,actor,tx.now()));}
    private static User requireUser(ReleaseDatabase.Tx tx,String id){return tx.get("user",id,User.class).orElseThrow(() -> new ProtocolException("user-not-found"));}
    private static void requireAdmin(Principal actor){Checks.require(actor.securityAdmin(),"forbidden");}
    private static void bounds(int offset,int limit){Checks.require(offset>=0 && offset<=100000 && limit>=1 && limit<=200,"invalid-page");}
}
