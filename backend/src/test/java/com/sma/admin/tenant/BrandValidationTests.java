package com.sma.admin.tenant;

import static org.assertj.core.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sma.admin.common.BizException;
import com.sma.admin.tenant.internal.service.*;

class BrandValidationTests {
    private final BrandValidation validation=new BrandValidation(new LoginTemplates());
    @Test void templatesAreFiniteAndDefaultsHaveNoInheritedLegalIdentity() {
        var catalog=new LoginTemplates();assertThat(catalog.all()).extracting(LoginTemplates.Template::id).containsExactly("login1","login2","login3");
        assertThat(catalog.effective("https://evil/code.js")).isEqualTo("login1");
        var defaults=validation.validate(BrandContent.defaults("Tenant A"));
        assertThat(defaults.legal().copyrightOwner()).isEmpty();assertThat(defaults.legal().icpRecord()).isEmpty();
        for(var template:catalog.all()) assertThatCode(() -> validation.validate(replace(defaults,template.id(),defaults.links(),Map.of()))).doesNotThrowAnyException();
    }
    @Test void unsafeLinksCodeAndUnknownTemplatesAreRejectedInsteadOfBeingExecuted() {
        var content=BrandContent.defaults("Tenant A");
        for(String link:new String[]{"javascript:alert(1)","data:text/html,x","//evil.example","https://user:pass@evil.example","https://good.example/\r\nx"}) {
            assertThatThrownBy(() -> validation.validate(replace(content,"login1",new BrandContent.Links(link,"","","",""),Map.of()))).isInstanceOf(BizException.class);
        }
        assertThatThrownBy(() -> validation.validate(replace(content,"../../views/Evil.vue",content.links(),Map.of()))).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> validation.validate(BrandContent.defaults("<script>alert(1)</script>"))).isInstanceOf(BizException.class);
    }
    @Test void legalInformationCannotSilentlyInheritPlatformRegistration() {
        var content=BrandContent.defaults("Tenant A");
        assertThatThrownBy(() -> validation.validate(replace(content,"login1",content.links(),Map.of(BrandContent.Field.ICP_RECORD,BrandContent.OverrideMode.INHERIT)))).isInstanceOf(BizException.class);
        assertThatCode(() -> validation.validate(replace(content,"login1",content.links(),Map.of(BrandContent.Field.ICP_RECORD,BrandContent.OverrideMode.HIDE)))).doesNotThrowAnyException();
    }
    private BrandContent replace(BrandContent c,String template,BrandContent.Links links,Map<BrandContent.Field,BrandContent.OverrideMode> overrides) {
        return new BrandContent(1,c.identity(),new BrandContent.Login(template,c.login().title(),c.login().description(),false),c.legal(),links,c.appearance(),c.assets(),overrides);
    }
}
