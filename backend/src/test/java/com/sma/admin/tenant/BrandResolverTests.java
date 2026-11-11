package com.sma.admin.tenant;
import static org.assertj.core.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sma.admin.tenant.internal.service.*;
class BrandResolverTests {
    @Test void hiddenValuesNeverLeakAndInheritancePreservesTenantIdentityAndLegalOwnership() {
        var defaults=BrandContent.defaults("Platform");var content=BrandContent.defaults("Tenant");
        defaults=new BrandContent(1,defaults.identity(),new BrandContent.Login("login2",new BrandContent.Text("平台欢迎","Welcome"),defaults.login().description(),false),
                new BrandContent.Legal("Platform owner",2020,"https://platform.example","ICP platform","https://platform.example","",""),
                new BrandContent.Links("https://platform.example","https://help.example","","",""),defaults.appearance(),Map.of(),Map.of());
        content=new BrandContent(1,content.identity(),content.login(),new BrandContent.Legal("Hidden owner",2024,"https://hidden.example","","","",""),content.links(),content.appearance(),
                Map.of(BrandContent.AssetSlot.LOGO_LIGHT,12L),Map.of(BrandContent.Field.WELCOME_TITLE,BrandContent.OverrideMode.INHERIT,
                BrandContent.Field.HELP,BrandContent.OverrideMode.INHERIT,BrandContent.Field.COPYRIGHT,BrandContent.OverrideMode.HIDE,BrandContent.Field.LOGO_LIGHT,BrandContent.OverrideMode.HIDE));
        var result=new BrandResolver().resolve(content,defaults);
        assertThat(result.identity().systemName()).isEqualTo("Tenant");assertThat(result.login().templateId()).isEqualTo("login1");
        assertThat(result.login().title().zhCN()).isEqualTo("平台欢迎");assertThat(result.links().help()).isEqualTo("https://help.example");
        assertThat(result.legal().copyrightOwner()).isEmpty();assertThat(result.legal().copyrightUrl()).isEmpty();assertThat(result.legal().copyrightStartYear()).isNull();
        assertThat(result.legal().icpRecord()).isEmpty();assertThat(result.assets()).isEmpty();assertThat(result.overrides()).isEmpty();
        assertThatThrownBy(()->new BrandDefaultsProperties("v1",new BrandContent(1,result.identity(),result.login(),result.legal(),result.links(),result.appearance(),Map.of(BrandContent.AssetSlot.LOGO_LIGHT,12L),Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
