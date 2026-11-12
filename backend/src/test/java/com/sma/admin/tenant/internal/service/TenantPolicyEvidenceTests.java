package com.sma.admin.tenant.internal.service;

import static org.assertj.core.api.Assertions.*;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.sma.admin.common.deployment.DeploymentEvidence;
import com.sma.admin.tenant.BrandContent;

class TenantPolicyEvidenceTests {
    private static final DeploymentEvidence.Challenge C=new DeploymentEvidence.Challenge("a".repeat(64),"b".repeat(64),"c".repeat(64));
    private static final TenantPlatformProperties PLATFORM=new TenantPlatformProperties(1L,Set.of(1L,2L),Set.of("example.test","admin.test"));
    private static final DomainVerificationProperties DOMAINS=new DomainVerificationProperties(Set.of("203.0.113.1"),null,null,null);
    private static final BrandDefaultsProperties BRAND=new BrandDefaultsProperties(null,null);
    private static String proof(TenantPlatformProperties platform,DomainVerificationProperties domains,BrandDefaultsProperties brand){return new TenantPolicyEvidence(platform,domains,brand).collect(C).digest();}

    @Test void platformAuthorizationAndTrustedIngressCannotDifferAcrossNodes(){
        var baseline=proof(PLATFORM,DOMAINS,BRAND);
        assertThat(proof(new TenantPlatformProperties(1L,Set.of(2L,1L),Set.of("admin.test","example.test")),DOMAINS,BRAND)).isEqualTo(baseline);
        assertThat(proof(new TenantPlatformProperties(2L,PLATFORM.platformAdminUserIds(),PLATFORM.bootstrapHosts()),DOMAINS,BRAND)).isNotEqualTo(baseline);
        assertThat(proof(new TenantPlatformProperties(1L,Set.of(1L),PLATFORM.bootstrapHosts()),DOMAINS,BRAND)).isNotEqualTo(baseline);
        assertThat(proof(new TenantPlatformProperties(1L,PLATFORM.platformAdminUserIds(),Set.of("different.test")),DOMAINS,BRAND)).isNotEqualTo(baseline);
        for(var candidate:java.util.List.of(new DomainVerificationProperties(Set.of("203.0.113.2"),null,null,null),
                new DomainVerificationProperties(DOMAINS.ingressAddresses(),Duration.ofSeconds(2),null,null),
                new DomainVerificationProperties(DOMAINS.ingressAddresses(),null,Duration.ofSeconds(3),null),
                new DomainVerificationProperties(DOMAINS.ingressAddresses(),null,null,Duration.ofSeconds(11))))
            assertThat(proof(PLATFORM,candidate,BRAND)).isNotEqualTo(baseline);
    }

    @Test void brandFallbackMaterialIsComparedEvenWhenRevisionNameIsUnchanged(){
        var baseline=proof(PLATFORM,DOMAINS,BRAND);
        assertThat(proof(PLATFORM,DOMAINS,new BrandDefaultsProperties(BRAND.revision(),BrandContent.defaults("Other name")))).isNotEqualTo(baseline);
        assertThat(proof(PLATFORM,DOMAINS,new BrandDefaultsProperties("other-revision",BRAND.content()))).isNotEqualTo(baseline);
        var old=BRAND.content();
        var appearance=new BrandContent(old.schemaVersion(),old.identity(),old.login(),old.legal(),old.links(),new BrandContent.Appearance("","iris",false),old.assets(),old.overrides());
        assertThat(proof(PLATFORM,DOMAINS,new BrandDefaultsProperties(BRAND.revision(),appearance))).isNotEqualTo(baseline);
        var login=new BrandContent(old.schemaVersion(),old.identity(),new BrandContent.Login("login2",old.login().title(),old.login().description(),false),old.legal(),old.links(),old.appearance(),old.assets(),old.overrides());
        assertThat(proof(PLATFORM,DOMAINS,new BrandDefaultsProperties(BRAND.revision(),login))).isNotEqualTo(baseline);
    }
}
