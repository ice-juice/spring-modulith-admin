package com.sma.admin.auth.internal.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.auth.internal.persistence.*;
import com.sma.admin.common.security.CurrentUser;

@Service
public class SecurityMaintenance {
    private final SecurityChallengeMapper challenges;private final SecurityThrottleMapper limits;private final TotpCredentialMapper totps;private final SecurityPasswordMapper passwords;
    private final SessionGuardMapper guard;
    private final SecuritySecrets secrets;
    public SecurityMaintenance(SecurityChallengeMapper challenges,SecurityThrottleMapper limits,TotpCredentialMapper totps,SecurityPasswordMapper passwords,SessionGuardMapper guard,SecuritySecrets secrets){this.challenges=challenges;this.limits=limits;this.totps=totps;this.passwords=passwords;this.guard=guard;this.secrets=secrets;}
    /** Tenant-scoped, bounded and restartable. Never changes authentication state or replay counters. */
    @Transactional(timeout=5) public int rotateEncryption(){
        if(!secrets.configured()) return 0;
        var rows=totps.selectList(new LambdaQueryWrapper<TotpCredentialEntity>()
                .apply("(CAST(SUBSTRING_INDEX(secret_ciphertext,'.',2) AS BINARY) <> CAST({0} AS BINARY) OR CAST(SUBSTRING_INDEX(pending_secret_ciphertext,'.',2) AS BINARY) <> CAST({0} AS BINARY))", "v1."+secrets.encryptionId())
                .orderByAsc(TotpCredentialEntity::getId).last("LIMIT 100 FOR UPDATE SKIP LOCKED"));
        for(var row:rows){
            String context=row.getTenantId()+":"+row.getUserId()+":"+row.getPortal()+":totp";
            if(row.getSecretCiphertext()!=null) row.setSecretCiphertext(secrets.reencrypt(row.getSecretCiphertext(),context));
            if(row.getPendingSecretCiphertext()!=null) row.setPendingSecretCiphertext(secrets.reencrypt(row.getPendingSecretCiphertext(),context+":pending"));
            totps.updateById(row);
        }
        return rows.size();
    }
    @Transactional(timeout=5) public void expire(){
        challenges.removeExpired();limits.removeExpired();
        var rows=totps.selectList(new LambdaQueryWrapper<TotpCredentialEntity>().lt(TotpCredentialEntity::getPendingExpiresAt,limits.now()).last("LIMIT 200 FOR UPDATE SKIP LOCKED"));
        for(var row:rows){row.setPendingSecretCiphertext(null);row.setPendingChallengeId(null);row.setPendingExpiresAt(null);totps.updateById(row);}
    }
    @Transactional public void removeAccount(long userId){
        guard.lock(userId,CurrentUser.tenantId());
        var rows=totps.selectList(new LambdaQueryWrapper<TotpCredentialEntity>().eq(TotpCredentialEntity::getUserId,userId).last("FOR UPDATE"));
        for(var row:rows){row.setEnabled(false);row.setSecretCiphertext(null);row.setEnabledAt(null);row.setLastAcceptedStep(null);row.setPendingSecretCiphertext(null);row.setPendingExpiresAt(null);row.setPendingChallengeId(null);totps.updateById(row);totps.deleteById(row.getId());}
        var passwordRows=passwords.selectList(new LambdaQueryWrapper<SecurityPasswordEntity>().eq(SecurityPasswordEntity::getUserId,userId).last("FOR UPDATE"));
        for(var row:passwordRows){row.setPasswordHash("REVOKED");row.setPepperKeyId("REVOKED");passwords.updateById(row);passwords.deleteById(row.getId());}
        challenges.delete(new LambdaQueryWrapper<SecurityChallengeEntity>().eq(SecurityChallengeEntity::getUserId,userId));
    }
}
