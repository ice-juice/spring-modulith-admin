package com.sma.admin.tenant.internal.service;

import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.security.*;
import com.sma.admin.tenant.internal.persistence.*;

/** Depends on owned reference metadata only, preventing FileService -> policy -> FileService construction cycles. */
@Component
public class BrandFilePolicy implements FileAccessPolicy {
    private final BrandReferenceMapper references;
    public BrandFilePolicy(BrandReferenceMapper references) { this.references=references; }
    @Override public boolean canRead(long fileId,SmaPrincipal user) {
        if(user.machine() || user.tenantId()!=CurrentUser.tenantId() || !user.portal().equals(CurrentUser.portal())) return false;
        if(user.permissions().stream().noneMatch(p->java.util.Set.of("tenant:brand:view","tenant:brand:edit","tenant:brand:publish","tenant:brand:rollback").contains(p))) return false;
        return references.selectCount(new LambdaQueryWrapper<BrandReferenceEntity>().eq(BrandReferenceEntity::getPortal,user.portal()).eq(BrandReferenceEntity::getFileId,fileId))>0;
    }
    @Override public boolean inUse(long fileId) { return references.currentReference(CurrentUser.portal(),fileId)!=null; }
}
