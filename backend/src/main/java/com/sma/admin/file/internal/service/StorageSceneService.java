package com.sma.admin.file.internal.service;

import java.util.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.file.*;
import com.sma.admin.file.internal.persistence.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Scene draft, publish and runtime selection share tenant ownership. Lock scene before sorted profile rows. */
@Service @Transactional(readOnly=true) @EnableConfigurationProperties(FileSceneLimits.class)
public class StorageSceneService {
    public record Configuration(Set<Long> profileIds,Long defaultProfileId,Set<FileFormat> formats,Set<UploadMode> modes,long maximumBytes,int retentionDays){
        public Configuration{profileIds=profileIds==null?Set.of():Set.copyOf(profileIds);formats=formats==null?Set.of():Set.copyOf(formats);modes=modes==null?Set.of():Set.copyOf(modes);}
    }
    public record Input(String code,String name,Configuration configuration){}
    public record View(Long id,String application,String code,String name,Long version,Long publishedVersion,boolean enabled,Configuration draft){}
    public record Profile(Long id,Long configurationVersion,Long credentialVersion,StorageProvider provider){}
    public record Publication(Long sceneId,Long version,Configuration configuration,List<Profile> profiles){public Publication{profiles=List.copyOf(profiles);}}
    public record Resolved(long tenantId,long tenantGeneration,long sceneId,long sceneVersion,String application,String scene,Configuration configuration,Profile profile,UploadMode mode){}
    private final SceneMapper scenes;private final SceneRevisionMapper revisions;private final StorageProfileMapper profiles;private final StorageRevisionMapper profileRevisions;
    private final FileSceneRegistry registry;private final FileSceneLimits limits;private final JsonMapper json;private final FileClock clock;
    private final TenantEntryQuery tenants;
    public StorageSceneService(SceneMapper scenes,SceneRevisionMapper revisions,StorageProfileMapper profiles,StorageRevisionMapper profileRevisions,FileSceneRegistry registry,FileSceneLimits limits,JsonMapper json,FileClock clock,TenantEntryQuery tenants){
        this.scenes=scenes;this.revisions=revisions;this.profiles=profiles;this.profileRevisions=profileRevisions;this.registry=registry;this.limits=limits;this.json=json;this.clock=clock;this.tenants=tenants;
    }
    @RequiresPermission("file:scene:view")
    public List<FileSceneCatalog.Definition> catalog(){ActorContext.tenantId();return registry.definitions();}
    @RequiresPermission("file:scene:view")
    public PageResult<View> page(PageQuery page,String keyword){
        ActorContext.tenantId();var query=new LambdaQueryWrapper<SceneEntity>().orderByDesc(SceneEntity::getCreatedAt).orderByDesc(SceneEntity::getId);
        if(keyword!=null && !keyword.isBlank())query.like(SceneEntity::getName,keyword);
        return PageResult.of(scenes.selectPage(page.toPage(Map.of("name","name","code","code","createdAt","created_at")),query),this::view);
    }
    @RequiresPermission("file:scene:view") public View get(long id){return view(owned(id,false));}
    @RequiresPermission("file:scene:view") public Publication published(long id){var scene=owned(id,false);return publication(scene);}
    @RequiresPermission("file:scene:manage") @Transactional
    public View create(Input input){
        var definition=validate(input);var row=new SceneEntity();row.setApplication(definition.application());row.setCode(input.code());row.setName(input.name().trim());
        row.setDraftJson(json.writeValueAsString(input.configuration()));row.setVersion(1L);row.setPublishedVersion(0L);row.setEnabled(false);
        profileSnapshots(definition.application(),input.configuration());
        try{scenes.insert(row);}catch(DuplicateKeyException duplicate){throw new BizException(FileErrorCode.SCENE_CONFLICT);}return view(row);
    }
    @RequiresPermission("file:scene:manage") @Transactional
    public View update(long id,long expectedVersion,Input input){
        var definition=validate(input);var row=owned(id,true);version(row,expectedVersion);
        if(!row.getCode().equals(input.code()) || !row.getApplication().equals(definition.application()))throw invalid();
        profileSnapshots(definition.application(),input.configuration());row.setName(input.name().trim());row.setDraftJson(json.writeValueAsString(input.configuration()));row.setVersion(Math.addExact(row.getVersion(),1));scenes.updateById(row);return view(row);
    }
    @RequiresPermission("file:scene:publish") @Transactional
    public View publish(long id,long expectedVersion){
        var row=owned(id,true);version(row,expectedVersion);var configuration=configuration(row);validate(new Input(row.getCode(),row.getName(),configuration));
        List<Profile> profiles=profileSnapshots(row.getApplication(),configuration);
        long version=Math.addExact(row.getVersion(),1);var published=new SceneRevisionEntity();published.setSceneId(row.getId());published.setVersion(version);
        published.setConfigurationJson(row.getDraftJson());published.setProfilesJson(json.writeValueAsString(profiles));revisions.insert(published);
        row.setVersion(version);row.setPublishedVersion(version);row.setEnabled(true);scenes.updateById(row);return view(row);
    }
    @RequiresPermission("file:scene:manage") @Transactional
    public View disable(long id,long expectedVersion){var row=owned(id,true);version(row,expectedVersion);row.setEnabled(false);row.setVersion(Math.addExact(row.getVersion(),1));scenes.updateById(row);return view(row);}
    /** Internal upload admission only. This returns a server-held snapshot, never a grant supplied by the client. */
    @Transactional
    public Resolved resolveUpload(FileSceneAccess access,Long preferredProfile,UploadMode mode){
        if(access==null || access.action()!=FileSceneAccess.Action.UPLOAD || mode==null)throw invalid();registry.authorize(access);
        long tenant=ActorContext.tenantId();var state=tenants.securityState(tenant).filter(TenantEntryQuery.SecurityState::acceptsBusiness)
                .orElseThrow(()->new org.springframework.security.access.AccessDeniedException("File tenant unavailable"));
        TenantEntryScope.optional().ifPresent(entry->{if(entry.tenantId()!=tenant || entry.securityGeneration()!=state.generation())throw new org.springframework.security.access.AccessDeniedException("File tenant entry retired");});
        var row=scenes.selectOne(new LambdaQueryWrapper<SceneEntity>().eq(SceneEntity::getCode,access.scene()).last("FOR UPDATE"));
        if(row==null || !row.getEnabled() || !row.getApplication().equals(access.application()))throw new BizException(FileErrorCode.SCENE_NOT_FOUND);
        var published=publication(row);validate(new Input(row.getCode(),row.getName(),published.configuration()));
        var configuration=published.configuration();long id=preferredProfile==null?configuration.defaultProfileId():preferredProfile;
        if(!configuration.profileIds().contains(id) || !configuration.modes().contains(mode))throw invalid();
        var selected=published.profiles().stream().filter(profile->profile.id()==id).findFirst().orElseThrow(StorageSceneService::invalid);
        var live=profile(id,row.getApplication());if(!live.getProvider().equals(selected.provider().name()))throw invalid();
        var snapshot=profileRevision(id,selected.configurationVersion());if(!snapshot.getCredentialVersion().equals(selected.credentialVersion()))throw invalid();available(snapshot);
        if(mode==UploadMode.DIRECT && selected.provider()==StorageProvider.LOCAL)throw invalid();
        return new Resolved(tenant,state.generation(),row.getId(),published.version(),row.getApplication(),row.getCode(),configuration,selected,mode);
    }
    private FileSceneCatalog.Definition validate(Input input){
        if(input==null || input.code()==null || input.name()==null || input.name().isBlank() || input.name().length()>128 || input.name().chars().anyMatch(Character::isISOControl) || input.configuration()==null)throw invalid();
        var definition=registry.require(input.code());var config=input.configuration();
        if(config.profileIds().isEmpty() || config.profileIds().size()>limits.maximumProfiles() || config.profileIds().stream().anyMatch(id->id<=0)
                || config.defaultProfileId()==null || !config.profileIds().contains(config.defaultProfileId()) || config.formats().isEmpty() || !definition.formats().containsAll(config.formats())
                || config.modes().isEmpty() || !definition.modes().containsAll(config.modes()) || config.maximumBytes()<1 || config.maximumBytes()>definition.maximumBytes()
                || config.maximumBytes()>limits.maximumBytes() || config.retentionDays()<1 || config.retentionDays()>limits.maximumRetentionDays())throw invalid();return definition;
    }
    /** Existing tasks keep their published configuration, but emergency stops, module limits and tenant generations remain live. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void validateUpload(Resolved saved,FileSceneAccess access){
        if(saved==null || access==null || access.action()!=FileSceneAccess.Action.UPLOAD || !saved.application().equals(access.application()) || !saved.scene().equals(access.scene()))throw invalid();
        ActorContext.requireTenant(saved.tenantId());registry.authorize(access);
        var tenant=tenants.securityState(saved.tenantId()).filter(TenantEntryQuery.SecurityState::acceptsBusiness).orElseThrow(()->new org.springframework.security.access.AccessDeniedException("File tenant unavailable"));
        if(tenant.generation()!=saved.tenantGeneration())throw new org.springframework.security.access.AccessDeniedException("Upload tenant generation retired");
        var row=scenes.selectOne(new LambdaQueryWrapper<SceneEntity>().eq(SceneEntity::getId,saved.sceneId()).last("FOR SHARE"));
        if(row==null || !row.getEnabled() || !row.getCode().equals(saved.scene()) || !row.getApplication().equals(saved.application()))throw new BizException(FileErrorCode.SCENE_NOT_FOUND);
        validate(new Input(saved.scene(),row.getName(),saved.configuration()));
        var live=profile(saved.profile().id(),saved.application());if(!live.getProvider().equals(saved.profile().provider().name()))throw invalid();
        var revision=profileRevision(saved.profile().id(),saved.profile().configurationVersion());available(revision);
        if(!revision.getCredentialVersion().equals(saved.profile().credentialVersion()))throw invalid();
    }
    private List<Profile> profileSnapshots(String application,Configuration configuration){
        var result=new ArrayList<Profile>();
        for(Long id:configuration.profileIds().stream().sorted().toList()){
            var profile=profile(id,application);var provider=StorageProvider.valueOf(profile.getProvider());
            if(provider==StorageProvider.LOCAL && configuration.modes().contains(UploadMode.DIRECT))throw invalid();
            result.add(new Profile(id,profile.getConfigurationVersion(),profile.getCredentialVersion(),provider));
        }
        return List.copyOf(result);
    }
    private StorageProfileEntity profile(long id,String application){
        var row=profiles.selectOne(new LambdaQueryWrapper<StorageProfileEntity>().eq(StorageProfileEntity::getId,id).last("FOR SHARE"));
        if(row==null || !row.getUploadsEnabled() || !row.getReadsEnabled())throw new BizException(FileErrorCode.STORAGE_PROFILE_NOT_FOUND);
        var revision=profileRevision(id,row.getConfigurationVersion());available(revision);
        if(!json.readValue(revision.getApplicationsJson(),new TypeReference<List<String>>(){}).contains(application))throw invalid();return row;
    }
    private void available(StorageRevisionEntity revision){if(revision.getCredentialExpiresAt()!=null && !revision.getCredentialExpiresAt().isAfter(clock.now()))throw new BizException(FileErrorCode.STORAGE_PROFILE_NOT_FOUND);}
    private StorageRevisionEntity profileRevision(long id,long version){var row=profileRevisions.selectOne(new LambdaQueryWrapper<StorageRevisionEntity>().eq(StorageRevisionEntity::getProfileId,id).eq(StorageRevisionEntity::getConfigurationVersion,version).last("FOR SHARE"));if(row==null)throw new BizException(FileErrorCode.STORAGE_PROFILE_NOT_FOUND);return row;}
    private Publication publication(SceneEntity row){
        var version=revisions.selectOne(new LambdaQueryWrapper<SceneRevisionEntity>().eq(SceneRevisionEntity::getSceneId,row.getId()).eq(SceneRevisionEntity::getVersion,row.getPublishedVersion()));
        if(version==null)throw new BizException(FileErrorCode.SCENE_NOT_FOUND);
        return new Publication(row.getId(),version.getVersion(),json.readValue(version.getConfigurationJson(),Configuration.class),json.readValue(version.getProfilesJson(),new TypeReference<List<Profile>>(){}));
    }
    private SceneEntity owned(long id,boolean lock){ActorContext.tenantId();var query=new LambdaQueryWrapper<SceneEntity>().eq(SceneEntity::getId,id);if(lock)query.last("FOR UPDATE");var row=scenes.selectOne(query);if(row==null)throw new BizException(FileErrorCode.SCENE_NOT_FOUND);return row;}
    private Configuration configuration(SceneEntity row){return json.readValue(row.getDraftJson(),Configuration.class);}
    private View view(SceneEntity row){return new View(row.getId(),row.getApplication(),row.getCode(),row.getName(),row.getVersion(),row.getPublishedVersion(),row.getEnabled(),configuration(row));}
    private void version(SceneEntity row,long expected){if(expected<=0 || row.getVersion()!=expected)throw new BizException(FileErrorCode.STORAGE_VERSION_CONFLICT);}
    static BizException invalid(){return new BizException(FileErrorCode.INVALID_SCENE);}
}
