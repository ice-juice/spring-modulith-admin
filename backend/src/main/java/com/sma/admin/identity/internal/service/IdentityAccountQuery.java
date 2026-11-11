package com.sma.admin.identity.internal.service;

import java.time.Instant;
import java.util.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.security.*;
import com.sma.admin.identity.internal.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Self-service projection only; not a public cross-subject business query contract. */
@Service
public class IdentityAccountQuery {
    public record GoogleLink(Long id,Instant createdAt){}
    public record Methods(String verifiedEmail,boolean passwordConfigured,boolean passwordEnabled,boolean googleEnabled,List<GoogleLink> googleLinks){
        public Methods{googleLinks=List.copyOf(googleLinks);}
        @Override public String toString(){return "AccountMethods[REDACTED]";}
    }
    private final IdentitySessionStore sessions;private final IdentityAuthority authority;
    private final IdentityEmailCredentialMapper credentials;private final IdentityExternalMapper external;private final TenantSecretCodec secrets;
    public IdentityAccountQuery(IdentitySessionStore sessions,IdentityAuthority authority,IdentityEmailCredentialMapper credentials,IdentityExternalMapper external,TenantSecretCodec secrets){
        this.sessions=sessions;this.authority=authority;this.credentials=credentials;this.external=external;this.secrets=secrets;
    }
    @Transactional
    public Methods current(AppUserPrincipal actor){
        IdentityAuthority.worker();var stamp=sessions.currentActor(actor);var live=authority.match(stamp.authority());
        var credential=credentials.selectOne(new LambdaQueryWrapper<IdentityEmailCredentialEntity>().eq(IdentityEmailCredentialEntity::getSubjectId,actor.subjectId()).last("FOR SHARE"));
        String email=null;
        if(credential!=null && credential.getEmailVerifiedAt()!=null){
            try(var value=secrets.decrypt(IdentityEmailAccountService.reference(credential),"verified-email-address",credential.getEmailEnvelope())){
                char[] chars=value.copy();try{email=new String(chars);}finally{Arrays.fill(chars,'\0');}
            }
        }
        var links=external.selectList(new LambdaQueryWrapper<IdentityExternalEntity>().eq(IdentityExternalEntity::getSubjectId,actor.subjectId())
                .eq(IdentityExternalEntity::getIssuer,"https://accounts.google.com").orderByAsc(IdentityExternalEntity::getId).last("LIMIT 100 FOR SHARE"))
                .stream().map(row->new GoogleLink(row.getId(),row.getCreatedAt())).toList();
        return new Methods(email,credential!=null && credential.getPasswordHash()!=null,live.realm().getPasswordEnabled(),live.configuration().getGoogleEnabled(),links);
    }
}
