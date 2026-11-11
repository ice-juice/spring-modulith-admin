package com.sma.admin.identity.internal.service;

import java.security.SecureRandom;
import java.util.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.identity.IdentityErrorCode;
import com.sma.admin.identity.internal.persistence.*;
import jakarta.validation.constraints.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service @Transactional(readOnly=true)
public class IdentityClientService {
    public enum Platform { WEB, ANDROID, IOS }
    public enum CookieSameSite { LAX, NONE }
    public record Input(@NotNull @Positive Long realmId,
            @NotBlank @Pattern(regexp="[a-z][a-z0-9.-]{0,63}") String code,
            @NotBlank @Size(max=128) String name,
            @NotBlank @Pattern(regexp="[a-z][a-z0-9.-]{0,63}") String application,
            @NotNull Platform platform,boolean enabled,boolean googleEnabled,@Size(max=255) String googleClientId,
            @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) @Size(max=8192) char[] googleSecret,
            @NotEmpty @Size(max=16) Set<@NotNull @Size(max=2048) String> callbackUris,
            @NotNull @Size(max=16) Set<@NotNull @Size(max=255) String> allowedOrigins,
            @Min(60) @Max(3600) int accessTokenSeconds,@Min(3600) @Max(2592000) int refreshTokenSeconds,CookieSameSite cookieSameSite,@Size(max=255)String googleAuthorizedParty){
        public Input(Long realmId,String code,String name,String application,Platform platform,boolean enabled,boolean googleEnabled,String googleClientId,
                     char[] googleSecret,Set<String> callbackUris,Set<String> allowedOrigins,int accessTokenSeconds,int refreshTokenSeconds){
            this(realmId,code,name,application,platform,enabled,googleEnabled,googleClientId,googleSecret,callbackUris,allowedOrigins,accessTokenSeconds,refreshTokenSeconds,null,null);
        }
        public Input(Long realmId,String code,String name,String application,Platform platform,boolean enabled,boolean googleEnabled,String googleClientId,
                     char[] googleSecret,Set<String> callbackUris,Set<String> allowedOrigins,int accessTokenSeconds,int refreshTokenSeconds,CookieSameSite cookieSameSite){
            this(realmId,code,name,application,platform,enabled,googleEnabled,googleClientId,googleSecret,callbackUris,allowedOrigins,accessTokenSeconds,refreshTokenSeconds,cookieSameSite,null);
        }
        @Override public String toString(){return "IdentityClientInput[REDACTED]";}
    }
    public record View(Long id,Long realmId,String code,String name,String application,Platform platform,String loginHandle,
            boolean enabled,Long version,Long securityVersion,Long configurationVersion,Long credentialVersion,
            boolean googleEnabled,String googleClientId,boolean secretConfigured,List<String> callbackUris,List<String> allowedOrigins,
            int accessTokenSeconds,int refreshTokenSeconds,CookieSameSite cookieSameSite,String googleAuthorizedParty) {}
    private final IdentityClientMapper clients;private final IdentityClientRevisionMapper revisions;
    private final IdentityRealmService realms;private final IdentityClientRules rules;private final TenantSecretCodec secrets;private final JsonMapper json;
    public IdentityClientService(IdentityClientMapper clients,IdentityClientRevisionMapper revisions,IdentityRealmService realms,
                                 IdentityClientRules rules,TenantSecretCodec secrets,JsonMapper json){
        this.clients=clients;this.revisions=revisions;this.realms=realms;this.rules=rules;this.secrets=secrets;this.json=json;
    }
    @RequiresPermission("identity:client:view")
    public PageResult<View> page(PageQuery page,Long realmId){
        ActorContext.tenantId();var query=new LambdaQueryWrapper<IdentityClientEntity>().orderByDesc(IdentityClientEntity::getCreatedAt).orderByDesc(IdentityClientEntity::getId);
        if(realmId!=null)query.eq(IdentityClientEntity::getRealmId,realmId);
        return PageResult.of(clients.selectPage(page.toPage(Map.of("createdAt","created_at")),query),this::view);
    }
    @RequiresPermission("identity:client:view") public View get(long id){return view(owned(id,false));}
    @RequiresPermission("identity:client:manage") @Transactional
    public View create(Input input){
        rules.validate(input);var realm=realms.owned(input.realmId(),true);
        if(!realm.getEnabled())throw invalid();
        var row=new IdentityClientEntity();row.setId(IdWorker.getId());row.setTenantId(ActorContext.tenantId());
        row.setRealmId(input.realmId());row.setCode(input.code());row.setName(input.name().trim());row.setApplication(input.application());
        row.setPlatform(input.platform().name());row.setEnabled(input.enabled());row.setVersion(1L);row.setSecurityVersion(1L);
        row.setConfigurationVersion(1L);row.setCredentialVersion(1L);
        byte[] handle=new byte[32];new SecureRandom().nextBytes(handle);row.setLoginHandle(Base64.getUrlEncoder().withoutPadding().encodeToString(handle));
        var revision=revision(row,input,secret(input),input.cookieSameSite()==null?CookieSameSite.LAX:input.cookieSameSite());
        revision.setGoogleAuthorizedParty(party(input,null));
        try{clients.insert(row);revisions.insert(revision);}catch(DuplicateKeyException duplicate){throw new BizException(IdentityErrorCode.DUPLICATE_CODE);}
        return view(row,revision);
    }
    @RequiresPermission("identity:client:manage") @Transactional
    public View update(long id,long expectedVersion,Input input){
        rules.validate(input);
        // Consistent realm -> client lock order; the association itself is immutable.
        var realm=realms.owned(input.realmId(),true);var row=owned(id,true);checkVersion(row,expectedVersion);
        if(!realm.getEnabled() || !row.getRealmId().equals(input.realmId()) || !row.getCode().equals(input.code())
                || !row.getApplication().equals(input.application()) || !row.getPlatform().equals(input.platform().name()))throw invalid();
        var previous=revision(row);var previousReference=reference(row);
        var cookieSameSite=input.cookieSameSite()==null?CookieSameSite.valueOf(previous.getCookieSameSite()):input.cookieSameSite();
        boolean replacing=secret(input)!=null;
        boolean removing=input.googleClientId()==null && previous.getSecretEnvelope()!=null;
        if(!Objects.equals(previous.getGoogleClientId(),input.googleClientId()) && input.platform()==Platform.WEB
                && input.googleClientId()!=null && previous.getSecretEnvelope()!=null && !replacing)throw invalid();
        row.setName(input.name().trim());row.setEnabled(input.enabled());advance(row);
        row.setConfigurationVersion(Math.addExact(row.getConfigurationVersion(),1));
        if(replacing || removing)row.setCredentialVersion(Math.addExact(row.getCredentialVersion(),1));
        IdentityClientRevisionEntity next;
        if(replacing || removing || previous.getSecretEnvelope()==null)next=revision(row,input,secret(input),cookieSameSite);
        else try(var value=secrets.decrypt(previousReference,"google-client-secret",previous.getSecretEnvelope())){
            char[] chars=value.copy();try{next=revision(row,input,chars,cookieSameSite);}finally{Arrays.fill(chars,'\0');}
        }
        next.setGoogleAuthorizedParty(party(input,previous));revisions.insert(next);clients.updateById(row);return view(row,next);
    }
    @RequiresPermission("identity:client:manage") @Transactional
    public View disable(long id,long expectedVersion){
        var row=owned(id,true);checkVersion(row,expectedVersion);row.setEnabled(false);advance(row);clients.updateById(row);
        return view(row); // No secret decryption, even during an emergency key outage.
    }
    private void advance(IdentityClientEntity row){row.setVersion(Math.addExact(row.getVersion(),1));row.setSecurityVersion(Math.addExact(row.getSecurityVersion(),1));}
    private void checkVersion(IdentityClientEntity row,long version){if(version<=0 || row.getVersion()!=version)throw new BizException(IdentityErrorCode.VERSION_CONFLICT);}
    private char[] secret(Input input){return input.googleSecret()==null || input.googleSecret().length==0?null:input.googleSecret();}
    private IdentityClientEntity owned(long id,boolean lock){
        ActorContext.tenantId();var query=new LambdaQueryWrapper<IdentityClientEntity>().eq(IdentityClientEntity::getId,id);if(lock)query.last("FOR UPDATE");
        var row=clients.selectOne(query);if(row==null)throw new BizException(IdentityErrorCode.CONFIGURATION_NOT_FOUND);return row;
    }
    private IdentityClientRevisionEntity revision(IdentityClientEntity row){
        var revision=revisions.selectOne(new LambdaQueryWrapper<IdentityClientRevisionEntity>().eq(IdentityClientRevisionEntity::getClientId,row.getId())
                .eq(IdentityClientRevisionEntity::getConfigurationVersion,row.getConfigurationVersion()));
        if(revision==null)throw new BizException(IdentityErrorCode.CONFIGURATION_NOT_FOUND);return revision;
    }
    private IdentityClientRevisionEntity revision(IdentityClientEntity row,Input input,char[] secret,CookieSameSite cookieSameSite){
        if(input.googleEnabled() && input.platform()==Platform.WEB && secret==null)throw invalid();
        var revision=new IdentityClientRevisionEntity();revision.setClientId(row.getId());revision.setConfigurationVersion(row.getConfigurationVersion());
        revision.setCredentialVersion(row.getCredentialVersion());revision.setGoogleEnabled(input.googleEnabled());revision.setGoogleClientId(input.googleClientId());
        if(secret!=null)revision.setSecretEnvelope(secrets.encrypt(reference(row),"google-client-secret",secret));
        revision.setCallbacksJson(json.writeValueAsString(new TreeSet<>(input.callbackUris())));revision.setOriginsJson(json.writeValueAsString(new TreeSet<>(input.allowedOrigins())));
        revision.setAccessTokenSeconds(input.accessTokenSeconds());revision.setRefreshTokenSeconds(input.refreshTokenSeconds());revision.setCookieSameSite(cookieSameSite.name());return revision;
    }
    private View view(IdentityClientEntity row){return view(row,revision(row));}
    private View view(IdentityClientEntity row,IdentityClientRevisionEntity revision){return new View(row.getId(),row.getRealmId(),row.getCode(),row.getName(),row.getApplication(),
            Platform.valueOf(row.getPlatform()),row.getLoginHandle(),row.getEnabled(),row.getVersion(),row.getSecurityVersion(),row.getConfigurationVersion(),row.getCredentialVersion(),
            revision.getGoogleEnabled(),revision.getGoogleClientId(),revision.getSecretEnvelope()!=null,json.readValue(revision.getCallbacksJson(),new TypeReference<List<String>>(){}),
            json.readValue(revision.getOriginsJson(),new TypeReference<List<String>>(){}),revision.getAccessTokenSeconds(),revision.getRefreshTokenSeconds(),CookieSameSite.valueOf(revision.getCookieSameSite()),revision.getGoogleAuthorizedParty());}
    private String party(Input input,IdentityClientRevisionEntity previous){
        if(input.googleClientId()==null)return null;
        if(input.googleAuthorizedParty()==null)return previous!=null && Objects.equals(previous.getGoogleClientId(),input.googleClientId())?previous.getGoogleAuthorizedParty():null;
        return input.googleAuthorizedParty().isEmpty()?null:input.googleAuthorizedParty();
    }
    static TenantConfigurationRef reference(IdentityClientEntity row){return new TenantConfigurationRef(row.getTenantId(),"identity",row.getId(),row.getConfigurationVersion(),row.getCredentialVersion());}
    private BizException invalid(){return new BizException(IdentityErrorCode.INVALID_CONFIGURATION);}
}
