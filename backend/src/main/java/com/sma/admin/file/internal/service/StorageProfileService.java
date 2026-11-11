package com.sma.admin.file.internal.service;

import java.time.Instant;
import java.util.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.file.*;
import com.sma.admin.file.internal.persistence.*;
import jakarta.validation.Validator;
import jakarta.validation.constraints.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Tenant management only. Saves immutable snapshots without contacting storage providers. */
@Service @Transactional(readOnly=true)
public class StorageProfileService {
    public enum Stop { UPLOADS, READS, ALL }
    public record Input(@NotBlank @Pattern(regexp="[a-z][a-z0-9.-]{0,63}")String code,@NotBlank @Size(max=128)String name,
            @NotNull StorageProvider provider,@Size(max=64)String localRoot,@Size(max=64)String region,@Size(max=512)String endpoint,
            @Size(max=63)String bucket,@Size(max=128)String prefix,
            @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) @Size(max=128)char[] accessKeyId,
            @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) @Size(max=512)char[] secretAccessKey,
            @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) @Size(max=8192)char[] sessionToken,Instant credentialExpiresAt,
            @NotEmpty @Size(max=100)Set<@NotNull @Pattern(regexp="[a-z][a-z0-9.-]{0,63}")String> applications,
            @NotNull Boolean uploadsEnabled,@NotNull Boolean readsEnabled){
        @Override public String toString(){return "StorageProfileInput[REDACTED]";}
    }
    public record View(Long id,String code,String name,StorageProvider provider,boolean uploadsEnabled,boolean readsEnabled,Long version,
            Long configurationVersion,Long credentialVersion,String localRoot,String region,String endpoint,String bucket,String prefix,
            boolean credentialsConfigured,String credentialHint,Instant credentialExpiresAt,List<String> applications){}
    public record Options(List<StorageProvider> providers,List<String> localRoots){}
    record Credentials(String accessKeyId,String secretAccessKey,String sessionToken){@Override public String toString(){return "StorageCredentials[REDACTED]";}}
    private final StorageProfileMapper profiles;private final StorageRevisionMapper revisions;private final StorageProfileRules rules;
    private final TenantSecretCodec secrets;private final JsonMapper json;private final Validator validator;private final FileClock clock;
    public StorageProfileService(StorageProfileMapper profiles,StorageRevisionMapper revisions,StorageProfileRules rules,TenantSecretCodec secrets,JsonMapper json,Validator validator,FileClock clock){
        this.profiles=profiles;this.revisions=revisions;this.rules=rules;this.secrets=secrets;this.json=json;this.validator=validator;this.clock=clock;
    }
    @RequiresPermission("file:storage:view")
    public Options options(){ActorContext.tenantId();return new Options(List.of(StorageProvider.values()),rules.localRoots());}
    @RequiresPermission("file:storage:view")
    public PageResult<View> page(PageQuery page,String keyword){
        ActorContext.tenantId();var query=new LambdaQueryWrapper<StorageProfileEntity>().orderByDesc(StorageProfileEntity::getCreatedAt).orderByDesc(StorageProfileEntity::getId);
        if(keyword!=null && !keyword.isBlank())query.like(StorageProfileEntity::getName,keyword);
        return PageResult.of(profiles.selectPage(page.toPage(Map.of("name","name","code","code","createdAt","created_at")),query),this::view);
    }
    @RequiresPermission("file:storage:view")
    public View get(long id){return view(owned(id,false));}
    @RequiresPermission("file:storage:manage") @Transactional
    public View create(Input input){
        var location=validate(input);var row=new StorageProfileEntity();row.setId(IdWorker.getId());row.setTenantId(ActorContext.tenantId());
        row.setCode(input.code());row.setName(input.name().trim());row.setProvider(input.provider().name());row.setUploadsEnabled(input.uploadsEnabled());row.setReadsEnabled(input.readsEnabled());
        row.setVersion(1L);row.setConfigurationVersion(1L);row.setCredentialVersion(1L);
        var revision=revision(row,input,location,null,null);
        try{profiles.insert(row);revisions.insert(revision);}catch(DuplicateKeyException duplicate){throw new BizException(FileErrorCode.STORAGE_PROFILE_CONFLICT);}
        return view(row,revision);
    }
    @RequiresPermission("file:storage:manage") @Transactional
    public View update(long id,long expectedVersion,Input input){
        var location=validate(input);var row=owned(id,true);version(row,expectedVersion);
        if(!row.getCode().equals(input.code()) || !row.getProvider().equals(input.provider().name()))throw StorageProfileRules.invalid();
        var old=revision(row);var oldReference=reference(row);row.setName(input.name().trim());row.setUploadsEnabled(input.uploadsEnabled());row.setReadsEnabled(input.readsEnabled());
        row.setVersion(Math.addExact(row.getVersion(),1));row.setConfigurationVersion(Math.addExact(row.getConfigurationVersion(),1));
        if(provided(input.accessKeyId()) || provided(input.secretAccessKey()))row.setCredentialVersion(Math.addExact(row.getCredentialVersion(),1));
        var next=revision(row,input,location,old,oldReference);revisions.insert(next);profiles.updateById(row);return view(row,next);
    }
    @RequiresPermission("file:storage:manage") @Transactional
    public View stop(long id,long expectedVersion,Stop scope){
        if(scope==null)throw StorageProfileRules.invalid();var row=owned(id,true);version(row,expectedVersion);
        if(scope==Stop.UPLOADS || scope==Stop.ALL)row.setUploadsEnabled(false);if(scope==Stop.READS || scope==Stop.ALL)row.setReadsEnabled(false);
        // Emergency stop remains available without decrypting credentials, and stale edits cannot re-enable the profile.
        row.setVersion(Math.addExact(row.getVersion(),1));profiles.updateById(row);return view(row);
    }
    private StorageProfileRules.Location validate(Input input){
        if(input==null || !validator.validate(input).isEmpty() || input.name().chars().anyMatch(Character::isISOControl))throw StorageProfileRules.invalid();
        return rules.location(input.provider(),input.localRoot(),input.region(),input.endpoint(),input.bucket(),input.prefix());
    }
    private StorageRevisionEntity revision(StorageProfileEntity profile,Input input,StorageProfileRules.Location location,StorageRevisionEntity old,TenantConfigurationRef oldReference){
        var row=new StorageRevisionEntity();row.setProfileId(profile.getId());row.setConfigurationVersion(profile.getConfigurationVersion());row.setCredentialVersion(profile.getCredentialVersion());
        row.setLocalRoot(location.localRoot());row.setRegion(location.region());row.setEndpoint(location.endpoint());row.setBucket(location.bucket());row.setObjectPrefix(location.prefix());
        row.setApplicationsJson(json.writeValueAsString(input.applications().stream().sorted().toList()));
        if(input.provider()==StorageProvider.LOCAL){
            if(provided(input.accessKeyId()) || provided(input.secretAccessKey()) || provided(input.sessionToken()) || input.credentialExpiresAt()!=null)throw StorageProfileRules.invalid();return row;
        }
        boolean replacing=provided(input.accessKeyId()) || provided(input.secretAccessKey());
        if(replacing){
            if(!provided(input.accessKeyId()) || !provided(input.secretAccessKey()))throw StorageProfileRules.invalid();
            String id=new String(input.accessKeyId());if(!id.matches("[A-Za-z0-9_-]{8,128}") || !printable(input.secretAccessKey()) || provided(input.sessionToken()) && !printable(input.sessionToken()))throw StorageProfileRules.invalid();
            if(input.provider()==StorageProvider.R2 && provided(input.sessionToken()) || provided(input.sessionToken()) && input.credentialExpiresAt()==null)throw StorageProfileRules.invalid();
            var expiry=expiry(input.credentialExpiresAt());if(expiry!=null && !expiry.isAfter(clock.now()))throw StorageProfileRules.invalid();
            var material=new Credentials(id,new String(input.secretAccessKey()),provided(input.sessionToken())?new String(input.sessionToken()):null);
            char[] value=json.writeValueAsString(material).toCharArray();try{row.setSecretEnvelope(secrets.encrypt(reference(profile),"storage-credentials",value));}finally{Arrays.fill(value,'\0');}
            row.setCredentialHint("****"+id.substring(id.length()-4));row.setCredentialExpiresAt(expiry);
        }else{
            if(old==null || old.getSecretEnvelope()==null || provided(input.sessionToken()) || input.credentialExpiresAt()!=null && !expiry(input.credentialExpiresAt()).equals(old.getCredentialExpiresAt()))throw StorageProfileRules.invalid();
            if(old.getCredentialExpiresAt()!=null && !old.getCredentialExpiresAt().isAfter(clock.now()))throw StorageProfileRules.invalid();
            try(var secret=secrets.decrypt(oldReference,"storage-credentials",old.getSecretEnvelope())){
                char[] value=secret.copy();try{row.setSecretEnvelope(secrets.encrypt(reference(profile),"storage-credentials",value));}finally{Arrays.fill(value,'\0');}
            }
            row.setCredentialHint(old.getCredentialHint());row.setCredentialExpiresAt(old.getCredentialExpiresAt());
        }
        return row;
    }
    private StorageProfileEntity owned(long id,boolean lock){
        long tenant=ActorContext.tenantId();var query=new LambdaQueryWrapper<StorageProfileEntity>().eq(StorageProfileEntity::getId,id);if(lock)query.last("FOR UPDATE");
        var row=profiles.selectOne(query);if(row==null || row.getTenantId()!=tenant)throw new BizException(FileErrorCode.STORAGE_PROFILE_NOT_FOUND);return row;
    }
    private StorageRevisionEntity revision(StorageProfileEntity profile){
        var row=revisions.selectOne(new LambdaQueryWrapper<StorageRevisionEntity>().eq(StorageRevisionEntity::getProfileId,profile.getId()).eq(StorageRevisionEntity::getConfigurationVersion,profile.getConfigurationVersion()));
        if(row==null)throw new BizException(FileErrorCode.STORAGE_PROFILE_NOT_FOUND);return row;
    }
    private void version(StorageProfileEntity row,long expected){if(expected<=0 || row.getVersion()!=expected)throw new BizException(FileErrorCode.STORAGE_VERSION_CONFLICT);}
    static TenantConfigurationRef reference(StorageProfileEntity row){return new TenantConfigurationRef(row.getTenantId(),"file",row.getId(),row.getConfigurationVersion(),row.getCredentialVersion());}
    private View view(StorageProfileEntity row){return view(row,revision(row));}
    private View view(StorageProfileEntity row,StorageRevisionEntity revision){
        List<String> applications=json.readValue(revision.getApplicationsJson(),new TypeReference<List<String>>(){});
        return new View(row.getId(),row.getCode(),row.getName(),StorageProvider.valueOf(row.getProvider()),row.getUploadsEnabled(),row.getReadsEnabled(),row.getVersion(),row.getConfigurationVersion(),row.getCredentialVersion(),
                revision.getLocalRoot(),revision.getRegion(),revision.getEndpoint(),revision.getBucket(),revision.getObjectPrefix(),revision.getSecretEnvelope()!=null,revision.getCredentialHint(),revision.getCredentialExpiresAt(),List.copyOf(applications));
    }
    private boolean provided(char[] value){return value!=null && value.length!=0;}
    private Instant expiry(Instant value){return value==null?null:value.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);}
    private boolean printable(char[] value){for(char c:value)if(c<33 || c>126)return false;return true;}
}
