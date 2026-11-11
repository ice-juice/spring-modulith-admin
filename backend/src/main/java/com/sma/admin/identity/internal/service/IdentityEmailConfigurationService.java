package com.sma.admin.identity.internal.service;

import java.util.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.identity.IdentityErrorCode;
import com.sma.admin.identity.internal.persistence.*;
import com.sma.admin.mail.MailTemplateQuery;
import jakarta.validation.Validator;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true)
public class IdentityEmailConfigurationService {
    public record Input(boolean enabled,
            @NotBlank @Pattern(regexp="[a-z][a-z0-9.-]{0,63}")String registrationTemplate,
            @NotBlank @Pattern(regexp="[a-z][a-z0-9.-]{0,63}")String resetTemplate,
            @NotBlank @Pattern(regexp="[a-z][a-z0-9.-]{0,63}")String bindingTemplate){}
    public record View(Long id,Long clientId,String language,boolean enabled,Long version,String registrationTemplate,String resetTemplate,String bindingTemplate){}
    private final IdentityClientMapper clients;private final IdentityRealmService realms;private final IdentityEmailConfigurationMapper rows;
    private final MailTemplateQuery templates;private final Validator validator;
    public IdentityEmailConfigurationService(IdentityClientMapper clients,IdentityRealmService realms,IdentityEmailConfigurationMapper rows,MailTemplateQuery templates,Validator validator){
        this.clients=clients;this.realms=realms;this.rows=rows;this.templates=templates;this.validator=validator;
    }
    @RequiresPermission("identity:client:view") public List<View> list(long clientId){
        client(clientId,false);return rows.selectList(new LambdaQueryWrapper<IdentityEmailConfigurationEntity>().eq(IdentityEmailConfigurationEntity::getClientId,clientId)
                .orderByAsc(IdentityEmailConfigurationEntity::getLanguage)).stream().map(this::view).toList();
    }
    @RequiresPermission("identity:client:manage") @Transactional
    public View put(long clientId,String language,long expectedVersion,Input input){
        if(input==null || !validator.validate(input).isEmpty() || !Set.of("zh-CN","en-US").contains(language==null?"":language) || expectedVersion<0)throw invalid();
        var first=client(clientId,false);var realm=realms.owned(first.getRealmId(),true);var client=client(clientId,true);
        var row=rows.selectOne(new LambdaQueryWrapper<IdentityEmailConfigurationEntity>().eq(IdentityEmailConfigurationEntity::getClientId,clientId)
                .eq(IdentityEmailConfigurationEntity::getLanguage,language).last("FOR UPDATE"));
        if(row==null && expectedVersion!=0 || row!=null && row.getVersion()!=expectedVersion)throw new BizException(IdentityErrorCode.VERSION_CONFLICT);
        if(input.enabled()){
            if(!realm.getEnabled() || !client.getEnabled() || !realm.getPasswordEnabled())throw invalid();
            available("registration",input.registrationTemplate(),language);available("password-reset",input.resetTemplate(),language);available("email-binding",input.bindingTemplate(),language);
        }
        boolean create=row==null;
        if(create){row=new IdentityEmailConfigurationEntity();row.setClientId(clientId);row.setLanguage(language);row.setVersion(1L);}
        else row.setVersion(Math.addExact(row.getVersion(),1));
        row.setEnabled(input.enabled());row.setRegistrationTemplate(input.registrationTemplate());row.setResetTemplate(input.resetTemplate());row.setBindingTemplate(input.bindingTemplate());
        if(create)rows.insert(row);else rows.updateById(row);return view(row);
    }
    private void available(String scene,String code,String language){
        if(templates.available("identity",scene,code,language,Map.of("code","000001","minutes","10")).isEmpty())throw invalid();
    }
    private IdentityClientEntity client(long id,boolean lock){
        ActorContext.tenantId();var query=new LambdaQueryWrapper<IdentityClientEntity>().eq(IdentityClientEntity::getId,id);if(lock)query.last("FOR UPDATE");
        var row=clients.selectOne(query);if(row==null)throw new BizException(IdentityErrorCode.CONFIGURATION_NOT_FOUND);return row;
    }
    private View view(IdentityEmailConfigurationEntity row){return new View(row.getId(),row.getClientId(),row.getLanguage(),row.getEnabled(),row.getVersion(),
            row.getRegistrationTemplate(),row.getResetTemplate(),row.getBindingTemplate());}
    private BizException invalid(){return new BizException(IdentityErrorCode.INVALID_CONFIGURATION);}
}
