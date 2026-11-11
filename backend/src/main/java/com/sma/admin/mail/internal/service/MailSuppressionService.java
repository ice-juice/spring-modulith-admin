package com.sma.admin.mail.internal.service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.security.*;
import com.sma.admin.mail.internal.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Callers hold the profile row lock so suppression checks and receipt inserts are serialized. */
@Service @Transactional(propagation=Propagation.MANDATORY)
public class MailSuppressionService {
    private final TenantSecretCodec secrets;
    private final MailProfileMapper profiles;
    private final MailSuppressionMapper rows;
    public MailSuppressionService(TenantSecretCodec secrets,MailProfileMapper profiles,MailSuppressionMapper rows) {
        this.secrets=secrets;this.profiles=profiles;this.rows=rows;
    }
    public String recipientKey(MailProfileEntity profile,String application,String address) {
        ActorContext.requireTenant(profile.getTenantId());
        var reference=new TenantConfigurationRef(profile.getTenantId(),"mail",profile.getId(),1,1);
        if(profile.getRecipientKeyEnvelope()==null) {
            byte[] key=new byte[32];new SecureRandom().nextBytes(key);
            char[] encoded=Base64.getEncoder().encodeToString(key).toCharArray();
            try {profile.setRecipientKeyEnvelope(secrets.encrypt(reference,"recipient-index-key",encoded));profiles.updateById(profile);}
            finally {Arrays.fill(key,(byte)0);Arrays.fill(encoded,'\0');}
        }
        try(var secret=secrets.decrypt(reference,"recipient-index-key",profile.getRecipientKeyEnvelope())) {
            char[] encoded=secret.copy();byte[] key=null;
            try {
                key=Base64.getDecoder().decode(new String(encoded));
                Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));
                // Conservative case folding prevents alternate mailbox casing from bypassing a suppression.
                return HexFormat.of().formatHex(mac.doFinal((application+"\n"+address.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8)));
            }catch(GeneralSecurityException impossible){throw new IllegalStateException("Mail recipient indexing unavailable",impossible);}
            finally {Arrays.fill(encoded,'\0');if(key!=null)Arrays.fill(key,(byte)0);}
        }
    }
    public boolean blocked(long profileId,String application,String key) {
        return find(profileId,application,key)!=null;
    }
    public void block(MailDeliveryEntity delivery,String reason) {
        if(delivery.getRecipientKey()==null)throw new IllegalStateException("Legacy mail has no recipient index");
        var row=find(delivery.getProfileId(),delivery.getApplication(),delivery.getRecipientKey());
        if(row==null){row=new MailSuppressionEntity();row.setProfileId(delivery.getProfileId());row.setApplication(delivery.getApplication());
            row.setRecipientKey(delivery.getRecipientKey());row.setReason(reason);rows.insert(row);}
        else if(reason.equals("COMPLAINT") && !row.getReason().equals("COMPLAINT")){row.setReason(reason);rows.updateById(row);}
    }
    private MailSuppressionEntity find(long profileId,String application,String key) {
        ActorContext.tenantId();
        return rows.selectOne(new LambdaQueryWrapper<MailSuppressionEntity>().eq(MailSuppressionEntity::getProfileId,profileId)
                .eq(MailSuppressionEntity::getApplication,application).eq(MailSuppressionEntity::getRecipientKey,key).last("FOR UPDATE"));
    }
}
