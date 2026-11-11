package com.sma.admin.tenant.internal.service;

import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.config.AppearancePolicy;
import com.sma.admin.tenant.BrandContent;
import com.sma.admin.tenant.internal.persistence.*;

/** Resolves after the raw preference cache, so publishing a policy cannot leave cached effective preferences behind. */
@Component
public class BrandAppearancePolicy implements AppearancePolicy {
    private final BrandHeadMapper heads;private final BrandRevisionMapper revisions;private final JsonMapper json;
    public BrandAppearancePolicy(BrandHeadMapper heads,BrandRevisionMapper revisions,JsonMapper json) { this.heads=heads;this.revisions=revisions;this.json=json; }
    @Override public Decision current() {
        var head=heads.selectOne(new LambdaQueryWrapper<BrandHeadEntity>().eq(BrandHeadEntity::getPortal,CurrentUser.portal()));
        if(head==null || head.getPublishedRevisionId()==null) return Decision.defaults();
        var revision=revisions.selectOne(new LambdaQueryWrapper<BrandRevisionEntity>().eq(BrandRevisionEntity::getPortal,CurrentUser.portal()).eq(BrandRevisionEntity::getId,head.getPublishedRevisionId()));
        if(revision==null) throw new IllegalStateException("Published brand pointer has no revision");
        var appearance=json.readValue(revision.getContentJson(),BrandContent.class).appearance();
        return new Decision(appearance.defaultSkin(),appearance.allowPersonalSkin());
    }
}
