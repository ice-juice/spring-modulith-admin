package com.sma.admin.identity.internal.service;

import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.identity.IdentityErrorCode;
import com.sma.admin.identity.internal.persistence.*;
import jakarta.validation.Validator;
import jakarta.validation.constraints.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true)
public class IdentityRealmService {
    public record Input(@NotBlank @Pattern(regexp="[a-z][a-z0-9.-]{0,63}") String code,
                        @NotBlank @Size(max=128) String name,boolean enabled,boolean registrationEnabled,boolean passwordEnabled) {}
    public record View(Long id,String code,String name,boolean enabled,boolean registrationEnabled,boolean passwordEnabled,Long version,Long securityVersion) {}
    private final IdentityRealmMapper realms;private final Validator validator;
    public IdentityRealmService(IdentityRealmMapper realms,Validator validator){this.realms=realms;this.validator=validator;}
    @RequiresPermission("identity:realm:view")
    public PageResult<View> page(PageQuery page){ActorContext.tenantId();return PageResult.of(realms.selectPage(page.toPage(Map.of("createdAt","created_at")),
            new LambdaQueryWrapper<IdentityRealmEntity>().orderByDesc(IdentityRealmEntity::getCreatedAt).orderByDesc(IdentityRealmEntity::getId)),this::view);}
    @RequiresPermission("identity:realm:view") public View get(long id){return view(owned(id,false));}
    @RequiresPermission("identity:realm:manage") @Transactional
    public View create(Input input){
        validate(input);ActorContext.tenantId();var row=new IdentityRealmEntity();row.setCode(input.code());row.setVersion(1L);row.setSecurityVersion(1L);apply(row,input);
        try{realms.insert(row);}catch(DuplicateKeyException conflict){throw new BizException(IdentityErrorCode.DUPLICATE_CODE);}
        return view(row);
    }
    @RequiresPermission("identity:realm:manage") @Transactional
    public View update(long id,long version,Input input){
        validate(input);var row=owned(id,true);version(row,version);
        if(!row.getCode().equals(input.code()))throw new BizException(IdentityErrorCode.INVALID_CONFIGURATION);
        apply(row,input);advance(row);realms.updateById(row);return view(row);
    }
    @RequiresPermission("identity:realm:manage") @Transactional
    public View disable(long id,long version){var row=owned(id,true);version(row,version);row.setEnabled(false);advance(row);realms.updateById(row);return view(row);}
    IdentityRealmEntity owned(long id,boolean lock){
        ActorContext.tenantId();var query=new LambdaQueryWrapper<IdentityRealmEntity>().eq(IdentityRealmEntity::getId,id);
        if(lock)query.last("FOR UPDATE");var row=realms.selectOne(query);
        if(row==null)throw new BizException(IdentityErrorCode.CONFIGURATION_NOT_FOUND);return row;
    }
    private void validate(Input input){if(input==null || !validator.validate(input).isEmpty())throw new BizException(IdentityErrorCode.INVALID_CONFIGURATION);}
    private void version(IdentityRealmEntity row,long version){if(version<=0 || row.getVersion()!=version)throw new BizException(IdentityErrorCode.VERSION_CONFLICT);}
    private void advance(IdentityRealmEntity row){row.setVersion(Math.addExact(row.getVersion(),1));row.setSecurityVersion(Math.addExact(row.getSecurityVersion(),1));}
    private void apply(IdentityRealmEntity row,Input input){row.setName(input.name().trim());row.setEnabled(input.enabled());row.setRegistrationEnabled(input.registrationEnabled());row.setPasswordEnabled(input.passwordEnabled());}
    private View view(IdentityRealmEntity row){return new View(row.getId(),row.getCode(),row.getName(),row.getEnabled(),row.getRegistrationEnabled(),row.getPasswordEnabled(),row.getVersion(),row.getSecurityVersion());}
}
