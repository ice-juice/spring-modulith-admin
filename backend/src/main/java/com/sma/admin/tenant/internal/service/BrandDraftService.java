package com.sma.admin.tenant.internal.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.tenant.*;
import com.sma.admin.tenant.internal.persistence.*;

@Service @Transactional(readOnly=true)
public class BrandDraftService {
    private final BrandHeadMapper heads;
    private final BrandValidation validation;
    private final JsonMapper json;
    private final TenantRecordMapper tenants;
    private final BrandAssets assets;
    public BrandDraftService(BrandHeadMapper heads,BrandValidation validation,JsonMapper json,TenantRecordMapper tenants,BrandAssets assets) {
        this.heads=heads;this.validation=validation;this.json=json;this.tenants=tenants;this.assets=assets;
    }
    public record Draft(BrandContent content,long version,Long publishedRevisionId,String legacyBackgroundUrl) {}
    public Draft get() {
        require("tenant:brand:view");
        var head=heads.selectOne(scope());
        return head==null?new Draft(defaultContent(),0,null,null):view(head);
    }
    @Transactional public Draft save(long expectedVersion,BrandContent content) {
        require("tenant:brand:edit");validation.validate(content);
        if(expectedVersion<0) throw new BizException(TenantErrorCode.BRAND_INVALID);
        heads.ensure(IdWorker.getId(),CurrentUser.tenantId(),CurrentUser.portal(),json.writeValueAsString(defaultContent()),CurrentUser.userId());
        var head=heads.selectOne(scope().last("FOR UPDATE"));
        if(head.getDraftVersion()!=expectedVersion) throw new BizException(TenantErrorCode.STALE);
        assets.replaceDraft(content);
        if(content.assets().containsKey(BrandContent.AssetSlot.LOGIN_BACKGROUND)
                || content.overrides().getOrDefault(BrandContent.Field.LOGIN_BACKGROUND,BrandContent.OverrideMode.OVERRIDE)!=BrandContent.OverrideMode.OVERRIDE)
            head.setLegacyBackgroundUrl(null);
        head.setDraftJson(json.writeValueAsString(content));head.setDraftVersion(Math.addExact(head.getDraftVersion(),1));heads.updateById(head);
        return view(head); // Saving never changes the published pointer or creates a published revision.
    }
    private LambdaQueryWrapper<BrandHeadEntity> scope() { return new LambdaQueryWrapper<BrandHeadEntity>().eq(BrandHeadEntity::getPortal,CurrentUser.portal()); }
    private BrandContent defaultContent() {
        var tenant=tenants.selectById(CurrentUser.tenantId());
        if(tenant==null) throw new BizException(CommonErrorCode.DATA_NOT_FOUND);
        return BrandContent.defaults(tenant.getName());
    }
    private Draft view(BrandHeadEntity head) { return new Draft(json.readValue(head.getDraftJson(),BrandContent.class),head.getDraftVersion(),head.getPublishedRevisionId(),head.getLegacyBackgroundUrl()); }
    private void require(String permission) {
        var actor=CurrentUser.require();if(!actor.permissions().contains(permission)) throw new AccessDeniedException("Brand operation permission required");
    }
}
