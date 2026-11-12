package com.sma.admin.tenant.internal.service;

import org.springframework.stereotype.Component;
import com.sma.admin.common.deployment.*;

/** Platform authorization and public defaults must not depend on which node the balancer selects. */
@Component
public class TenantPolicyEvidence implements DeploymentEvidenceSource {
    private final TenantPlatformProperties platform;
    private final DomainVerificationProperties domains;
    private final BrandDefaultsProperties defaults;
    public TenantPolicyEvidence(TenantPlatformProperties platform,DomainVerificationProperties domains,BrandDefaultsProperties defaults){
        this.platform=platform;this.domains=domains;this.defaults=defaults;
    }
    @Override public String id(){return "tenant.platform-policy";}
    @Override public DeploymentEvidence.Proof collect(DeploymentEvidence.Challenge challenge){
        DeploymentEvidence.requireHost();
        var fields=new DeploymentFields().text("protocol","1").number("platform-tenant",platform.platformTenantId())
                .values("platform-operators",platform.platformAdminUserIds().stream().map(String::valueOf).toList())
                .values("bootstrap-hosts",platform.bootstrapHosts()).values("ingress-addresses",domains.ingressAddresses())
                .duration("dns-timeout",domains.dnsTimeout()).duration("connect-timeout",domains.connectTimeout()).duration("total-timeout",domains.totalTimeout())
                .text("brand.revision",defaults.revision());
        var brand=defaults.content();fields.number("brand.schema",brand.schemaVersion());
        text(fields,"brand.name",brand.identity().systemName());text(fields,"brand.short-name",brand.identity().shortName());text(fields,"brand.english-name",brand.identity().englishName());
        text(fields,"brand.template",brand.login().templateId());text(fields,"brand.title-zh",brand.login().title().zhCN());text(fields,"brand.title-en",brand.login().title().enUS());
        text(fields,"brand.description-zh",brand.login().description().zhCN());text(fields,"brand.description-en",brand.login().description().enUS());
        fields.flag("brand.blur",brand.login().backgroundBlur());
        text(fields,"brand.copyright",brand.legal().copyrightOwner());text(fields,"brand.copyright-url",brand.legal().copyrightUrl());
        text(fields,"brand.year",brand.legal().copyrightStartYear()==null?null:Integer.toString(brand.legal().copyrightStartYear()));
        text(fields,"brand.icp",brand.legal().icpRecord());text(fields,"brand.icp-url",brand.legal().icpUrl());
        text(fields,"brand.police",brand.legal().policeRecord());text(fields,"brand.police-url",brand.legal().policeUrl());
        text(fields,"brand.website",brand.links().website());text(fields,"brand.help",brand.links().help());text(fields,"brand.support",brand.links().support());
        text(fields,"brand.privacy",brand.links().privacy());text(fields,"brand.agreement",brand.links().agreement());
        text(fields,"brand.color",brand.appearance().primaryColor());text(fields,"brand.skin",brand.appearance().defaultSkin());
        fields.flag("brand.personal-skin",brand.appearance().allowPersonalSkin());
        return fields.proof(id(),challenge);
    }
    private static void text(DeploymentFields fields,String name,String value){
        fields.flag(name+".present",value!=null);if(value!=null)fields.text(name,value);
    }
}
