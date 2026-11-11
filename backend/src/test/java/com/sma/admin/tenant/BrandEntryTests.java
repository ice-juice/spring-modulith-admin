package com.sma.admin.tenant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.config.ParameterQuery;
import com.sma.admin.tenant.internal.persistence.*;
import com.sma.admin.tenant.internal.service.*;

class BrandEntryTests {
    private final BrandPublicationService publications=mock(BrandPublicationService.class);
    private final TenantRecordMapper tenants=mock(TenantRecordMapper.class);
    private final ParameterQuery parameters=mock(ParameterQuery.class);
    private final BrandEntryService service=new BrandEntryService(publications,tenants,parameters,new LoginTemplates());

    @Test void publicationIsProjectedWithoutFileIdsOverridesOrArbitraryPublicParameters() {
        var base=BrandContent.defaults("Published name");
        var content=new BrandContent(1,base.identity(),base.login(),base.legal(),base.links(),base.appearance(),
                Map.of(BrandContent.AssetSlot.LOGO_LIGHT,987654L),Map.of());
        when(publications.published()).thenReturn(Optional.of(new BrandPublicationService.Revision(101L,3,null,"defaults",content,java.time.Instant.now())));
        when(parameters.booleanValue("login.captchaEnabled",true)).thenReturn(false);
        try(var scope=TenantEntryScope.open(new TenantEntry(72,5,"tenant.example.com","partner",1))) {
            var view=service.entry();
            assertThat(view.entry()).isEqualTo(new BrandEntryView.Entry(72L,5L,"partner"));
            assertThat(view.branding().identity().systemName()).isEqualTo("Published name");
            assertThat(view.branding().assets()).containsExactlyEntriesOf(Map.of(BrandContent.AssetSlot.LOGO_LIGHT,"/api/tenant/brand-images/101/LOGO_LIGHT"));
            assertThat(view.login().captchaEnabled()).isFalse();assertThat(view.login().passwordEnabled()).isTrue();
            String output=tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(view);
            assertThat(output).doesNotContain("987654","overrides","defaultsRevision","publisherId","operationId","securityGeneration");
        }
        verify(parameters).booleanValue("login.captchaEnabled",true);verifyNoMoreInteractions(parameters);verifyNoInteractions(tenants);
    }

    @Test void unpublishedEntryUsesOnlyItsDirectoryNameAndCannotExposeDraftsOrPlatformLegalIdentity() {
        var tenant=new TenantRecordEntity();tenant.setId(73L);tenant.setName("Tenant seventy-three");
        when(tenants.selectById(73L)).thenReturn(tenant);when(publications.published()).thenReturn(Optional.empty());
        when(parameters.booleanValue("login.captchaEnabled",true)).thenReturn(true);
        try(var scope=TenantEntryScope.open(new TenantEntry(73,6,"other.example.com","admin",1))) {
            var view=service.entry();assertThat(view.branding().revisionId()).isNull();assertThat(view.branding().revision()).isZero();
            assertThat(view.branding().identity().systemName()).isEqualTo(tenant.getName());
            assertThat(view.branding().legal().copyrightOwner()).isEmpty();assertThat(view.branding().legal().icpRecord()).isEmpty();
            assertThat(view.branding().assets()).isEmpty();assertThat(view.login().captchaEnabled()).isTrue();
        }
        verify(publications).published();verifyNoMoreInteractions(publications);
    }

    @Test void imagesNeedVerifiedEntryAndMissingDataIsAnExplicitResult() {
        assertThatThrownBy(()->service.entry()).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(()->service.image(1,BrandContent.AssetSlot.LOGO_LIGHT)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        try(var scope=TenantEntryScope.open(new TenantEntry(73,6,"other.example.com","admin",1))) {
            when(publications.findPublicImage(1,BrandContent.AssetSlot.LOGO_LIGHT)).thenReturn(Optional.empty());
            assertThat(service.image(1,BrandContent.AssetSlot.LOGO_LIGHT)).isEmpty();
            when(publications.findPublicImage(2,BrandContent.AssetSlot.LOGO_LIGHT)).thenThrow(new org.springframework.security.access.AccessDeniedException("wrong entry"));
            assertThatThrownBy(()->service.image(2,BrandContent.AssetSlot.LOGO_LIGHT)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        }
    }
    @Test void aRetiredPublishedTemplateUsesLogin1WithoutRewritingItsImmutableRevision() {
        var base=BrandContent.defaults("Tenant");
        var stored=new BrandContent(1,base.identity(),new BrandContent.Login("retired-template",base.login().title(),base.login().description(),false),
                base.legal(),base.links(),base.appearance(),Map.of(),Map.of());
        when(publications.published()).thenReturn(Optional.of(new BrandPublicationService.Revision(101L,1,null,"defaults",stored,java.time.Instant.now())));
        try(var scope=TenantEntryScope.open(new TenantEntry(72,5,"tenant.example.com","admin",1))) {
            assertThat(service.entry().branding().login().templateId()).isEqualTo("login1");
            assertThat(stored.login().templateId()).isEqualTo("retired-template");
        }
    }
}
