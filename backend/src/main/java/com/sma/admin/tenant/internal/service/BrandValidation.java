package com.sma.admin.tenant.internal.service;

import java.util.Set;
import org.springframework.stereotype.Component;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.WebAddresses;
import com.sma.admin.tenant.BrandContent;
import com.sma.admin.tenant.TenantErrorCode;

@Component
public class BrandValidation {
    private final LoginTemplates templates;
    public BrandValidation(LoginTemplates templates) { this.templates=templates; }
    public BrandContent validate(BrandContent value) {
        if(value==null || value.schemaVersion()!=1 || value.identity()==null || value.login()==null || value.legal()==null
                || value.links()==null || value.appearance()==null) throw invalid();
        var id=value.identity();text(id.systemName(),128,true);text(id.shortName(),40,false);text(id.englishName(),128,false);
        var login=value.login();if(!templates.available(login.templateId())) throw invalid();
        localized(login.title(),120);localized(login.description(),500);
        var legal=value.legal();text(legal.copyrightOwner(),128,false);text(legal.icpRecord(),100,false);text(legal.policeRecord(),100,false);
        if(legal.copyrightStartYear()!=null && (legal.copyrightStartYear()<1900 || legal.copyrightStartYear()>9999)) throw invalid();
        link(legal.copyrightUrl());link(legal.icpUrl());link(legal.policeUrl());
        if(nonempty(legal.copyrightUrl())&&!nonempty(legal.copyrightOwner()) || nonempty(legal.icpUrl())&&!nonempty(legal.icpRecord())
                || nonempty(legal.policeUrl())&&!nonempty(legal.policeRecord())) throw invalid();
        var links=value.links();link(links.website());link(links.help());link(links.support());link(links.privacy());link(links.agreement());
        var appearance=value.appearance();
        if(appearance.defaultSkin()==null || !Set.of("iris","celadon","amber").contains(appearance.defaultSkin())) throw invalid();
        if(nonempty(appearance.primaryColor())&&!appearance.primaryColor().matches("#[a-fA-F0-9]{6}")) throw invalid();
        if(value.assets().size()>BrandContent.AssetSlot.values().length || value.assets().values().stream().anyMatch(asset -> asset<=0)) throw invalid();
        for(var entry:value.overrides().entrySet()) {
            if(entry.getValue()==BrandContent.OverrideMode.INHERIT && Set.of(BrandContent.Field.COPYRIGHT,BrandContent.Field.ICP_RECORD,BrandContent.Field.POLICE_RECORD).contains(entry.getKey()))
                throw invalid(); // A tenant cannot silently inherit another legal entity's registration/copyright.
        }
        return value;
    }
    private void localized(BrandContent.Text value,int max) { if(value==null) throw invalid();text(value.zhCN(),max,false);text(value.enUS(),max,false); }
    private void link(String value) { if(nonempty(value) && (value.length()>2048 || WebAddresses.httpLink(value).isEmpty())) throw invalid(); }
    private void text(String value,int max,boolean required) {
        if(value==null) { if(required) throw invalid();return; }
        if(value.length()>max || required&&value.isBlank() || value.codePoints().anyMatch(Character::isISOControl) || value.indexOf('<')>=0 || value.indexOf('>')>=0) throw invalid();
    }
    private boolean nonempty(String value) { return value!=null&&!value.isEmpty(); }
    private BizException invalid() { return new BizException(TenantErrorCode.BRAND_INVALID); }
}
