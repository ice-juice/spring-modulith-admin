package com.sma.admin.identity.internal.service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.sma.admin.common.security.*;
import com.sma.admin.identity.internal.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stable per-realm index key; changing the deployment's active envelope key does not change mailbox identity. */
@Service
public class IdentityEmailIndex {
    private final IdentityRealmService realms;private final IdentityEmailRealmKeyMapper keys;private final TenantSecretCodec secrets;
    public IdentityEmailIndex(IdentityRealmService realms,IdentityEmailRealmKeyMapper keys,TenantSecretCodec secrets){this.realms=realms;this.keys=keys;this.secrets=secrets;}
    @Transactional public String index(long realmId,String address){
        IdentityAuthority.worker();String normalized=IdentityEmailAddress.normalize(address);realms.owned(realmId,true);
        var row=keys.selectOne(new LambdaQueryWrapper<IdentityEmailRealmKeyEntity>().eq(IdentityEmailRealmKeyEntity::getRealmId,realmId).last("FOR UPDATE"));
        if(row==null){
            row=new IdentityEmailRealmKeyEntity();row.setId(IdWorker.getId());row.setTenantId(ActorContext.tenantId());row.setRealmId(realmId);
            byte[] key=new byte[32];new SecureRandom().nextBytes(key);char[] encoded=Base64.getEncoder().encodeToString(key).toCharArray();
            try{row.setKeyEnvelope(secrets.encrypt(reference(row),"email-index-key",encoded));keys.insert(row);}
            finally{Arrays.fill(key,(byte)0);Arrays.fill(encoded,'\0');}
        }
        try(var secret=secrets.decrypt(reference(row),"email-index-key",row.getKeyEnvelope())){
            char[] encoded=secret.copy();byte[] key=null;
            try{
                key=Base64.getDecoder().decode(new String(encoded));var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));
                return HexFormat.of().formatHex(mac.doFinal(("mailbox\n"+normalized).getBytes(StandardCharsets.UTF_8)));
            }catch(GeneralSecurityException impossible){throw new IllegalStateException("Mailbox indexing unavailable",impossible);}
            finally{Arrays.fill(encoded,'\0');if(key!=null)Arrays.fill(key,(byte)0);}
        }
    }
    private TenantConfigurationRef reference(IdentityEmailRealmKeyEntity row){return new TenantConfigurationRef(row.getTenantId(),"identity",row.getId(),1,1);}
}
