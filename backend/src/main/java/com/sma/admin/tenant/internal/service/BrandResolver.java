package com.sma.admin.tenant.internal.service;

import static com.sma.admin.tenant.BrandContent.Field.*;
import java.util.*;
import org.springframework.stereotype.Component;
import com.sma.admin.tenant.BrandContent;

/** Resolves all visibility before returning public data; hidden values never remain in a public DTO. */
@Component
public class BrandResolver {
    public BrandContent resolve(BrandContent draft,BrandContent defaults) {
        var identity=draft.identity();var login=draft.login();var legal=draft.legal();var links=draft.links();var other=defaults.links();
        var empty=new BrandContent.Text("","");
        Map<BrandContent.AssetSlot,Long> assets=new EnumMap<>(BrandContent.AssetSlot.class);
        for(var slot:BrandContent.AssetSlot.values()) {
            var mode=draft.overrides().getOrDefault(BrandContent.Field.valueOf(slot.name()),BrandContent.OverrideMode.OVERRIDE);
            Long id=mode==BrandContent.OverrideMode.HIDE?null:(mode==BrandContent.OverrideMode.INHERIT?defaults.assets():draft.assets()).get(slot);
            if(id!=null) assets.put(slot,id);
        }
        boolean copyright=mode(draft,COPYRIGHT)==BrandContent.OverrideMode.HIDE,icp=mode(draft,ICP_RECORD)==BrandContent.OverrideMode.HIDE,police=mode(draft,POLICE_RECORD)==BrandContent.OverrideMode.HIDE;
        return new BrandContent(1,new BrandContent.Identity(identity.systemName(),pick(draft,SHORT_NAME,identity.shortName(),defaults.identity().shortName(),""),pick(draft,ENGLISH_NAME,identity.englishName(),defaults.identity().englishName(),"")),
                new BrandContent.Login(login.templateId(),pick(draft,WELCOME_TITLE,login.title(),defaults.login().title(),empty),pick(draft,WELCOME_DESCRIPTION,login.description(),defaults.login().description(),empty),login.backgroundBlur()),
                new BrandContent.Legal(copyright?"":legal.copyrightOwner(),copyright?null:legal.copyrightStartYear(),copyright?"":legal.copyrightUrl(),icp?"":legal.icpRecord(),icp?"":legal.icpUrl(),police?"":legal.policeRecord(),police?"":legal.policeUrl()),
                new BrandContent.Links(pick(draft,WEBSITE,links.website(),other.website(),""),pick(draft,HELP,links.help(),other.help(),""),pick(draft,SUPPORT,links.support(),other.support(),""),pick(draft,PRIVACY,links.privacy(),other.privacy(),""),pick(draft,AGREEMENT,links.agreement(),other.agreement(),"")),
                new BrandContent.Appearance(pick(draft,PRIMARY_COLOR,draft.appearance().primaryColor(),defaults.appearance().primaryColor(),""),draft.appearance().defaultSkin(),draft.appearance().allowPersonalSkin()),assets,Map.of());
    }
    private BrandContent.OverrideMode mode(BrandContent content,BrandContent.Field field) { return content.overrides().getOrDefault(field,BrandContent.OverrideMode.OVERRIDE); }
    private <T>T pick(BrandContent content,BrandContent.Field field,T own,T inherited,T hidden) { return switch(mode(content,field)) { case HIDE->hidden;case INHERIT->inherited;case OVERRIDE->own; }; }
}
