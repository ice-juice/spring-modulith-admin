package com.sma.admin.mail.internal.service;

import java.util.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.security.*;
import com.sma.admin.mail.*;
import com.sma.admin.mail.internal.persistence.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service @Transactional(readOnly=true)
public class PublishedMailTemplateQuery implements MailTemplateQuery {
    private final MailTemplateMapper templates;private final MailTemplateVersionMapper versions;private final MailProfileMapper profiles;
    private final MailProfileRevisionMapper revisions;private final MailTemplateRenderer renderer;private final JsonMapper json;
    public PublishedMailTemplateQuery(MailTemplateMapper templates,MailTemplateVersionMapper versions,MailProfileMapper profiles,
            MailProfileRevisionMapper revisions,MailTemplateRenderer renderer,JsonMapper json){
        this.templates=templates;this.versions=versions;this.profiles=profiles;this.revisions=revisions;this.renderer=renderer;this.json=json;
    }
    @Override public Optional<Available> available(String application,String scene,String code,String language,Map<String,String> samples){
        var actor=ActorContext.require();
        if(actor.kind()!=ActorRef.Kind.SYSTEM_JOB && (actor.kind()!=ActorRef.Kind.ADMIN_USER || !CurrentUser.require().permissions().contains("mail:template:view")))
            throw new AccessDeniedException("Published mail template query requires host job or template permission");
        if(application==null || scene==null || code==null || language==null || samples==null || samples.size()>32)return Optional.empty();
        var template=templates.selectOne(new LambdaQueryWrapper<MailTemplateEntity>().eq(MailTemplateEntity::getApplication,application)
                .eq(MailTemplateEntity::getScene,scene).eq(MailTemplateEntity::getCode,code).eq(MailTemplateEntity::getLanguage,language));
        if(template==null || !template.getEnabled() || template.getPublishedVersion()==0)return Optional.empty();
        var version=versions.selectOne(new LambdaQueryWrapper<MailTemplateVersionEntity>().eq(MailTemplateVersionEntity::getTemplateId,template.getId())
                .eq(MailTemplateVersionEntity::getVersion,template.getPublishedVersion()));if(version==null)return Optional.empty();
        var profile=profiles.selectById(version.getProfileId());
        if(profile==null || !profile.getEnabled() || !profile.getCredentialVersion().equals(version.getCredentialVersion()))return Optional.empty();
        var revision=revisions.selectOne(new LambdaQueryWrapper<MailProfileRevisionEntity>().eq(MailProfileRevisionEntity::getProfileId,profile.getId())
                .eq(MailProfileRevisionEntity::getConfigurationVersion,profile.getConfigurationVersion()));
        if(revision==null || !json.readValue(revision.getApplicationsJson(),new TypeReference<List<String>>(){}).contains(application))return Optional.empty();
        var content=json.readValue(version.getContentJson(),MailTemplateContent.class);
        // Callers pass non-secret examples, permitting the sender-owned validator to check the exact variable contract.
        renderer.render(content,samples);
        return Optional.of(new Available(template.getId(),version.getVersion(),profile.getId()));
    }
}
