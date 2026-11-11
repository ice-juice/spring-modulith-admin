package com.sma.admin.tenant.internal.service;

import java.util.EnumMap;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sma.admin.common.*;
import com.sma.admin.common.security.TenantEntryScope;
import com.sma.admin.config.ParameterQuery;
import com.sma.admin.file.ImagePublication;
import com.sma.admin.tenant.*;
import com.sma.admin.tenant.internal.persistence.TenantRecordMapper;

@Service @Transactional(readOnly=true)
public class BrandEntryService {
    private final BrandPublicationService publications;
    private final TenantRecordMapper tenants;
    private final ParameterQuery parameters;
    private final LoginTemplates templates;

    public BrandEntryService(BrandPublicationService publications,TenantRecordMapper tenants,ParameterQuery parameters,LoginTemplates templates) {
        this.publications=publications;this.tenants=tenants;this.parameters=parameters;this.templates=templates;
    }

    public BrandEntryView entry() {
        var entry=TenantEntryScope.require();
        var published=publications.published();
        BrandContent content;
        Long revisionId=null;long revision=0;
        if(published.isPresent()) {
            var value=published.get();content=value.content();revisionId=value.id();revision=value.revision();
        } else {
            var tenant=tenants.selectById(entry.tenantId());
            if(tenant==null) throw new BizException(CommonErrorCode.DATA_NOT_FOUND);
            content=BrandContent.defaults(tenant.getName());
        }
        var assets=new EnumMap<BrandContent.AssetSlot,String>(BrandContent.AssetSlot.class);
        for(var slot:content.assets().keySet()) assets.put(slot,"/api/tenant/brand-images/"+revisionId+"/"+slot.name());
        var display=content.login();
        var effectiveLogin=new BrandContent.Login(templates.effective(display.templateId()),display.title(),display.description(),display.backgroundBlur());
        var branding=new BrandEntryView.Branding(revisionId,revision,content.identity(),effectiveLogin,content.legal(),
                content.links(),content.appearance(),assets,published.map(BrandPublicationService.Revision::legacyBackgroundUrl).orElse(null));
        // This is the same tenant-scoped policy used by authentication, never a public parameter enumeration.
        var login=new BrandEntryView.LoginCapabilities(true,parameters.booleanValue("login.captchaEnabled",true));
        return new BrandEntryView(new BrandEntryView.Entry(entry.tenantId(),entry.domainId(),entry.portal()),branding,login);
    }

    public Optional<ImagePublication.Image> image(long revisionId,BrandContent.AssetSlot slot) {
        TenantEntryScope.require();
        if(revisionId<=0) return Optional.empty();
        return publications.findPublicImage(revisionId,slot);
    }
}
