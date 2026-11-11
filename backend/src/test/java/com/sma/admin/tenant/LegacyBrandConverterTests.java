package com.sma.admin.tenant;

import static org.assertj.core.api.Assertions.*;
import static com.sma.admin.config.LegacyBrandSource.Key.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sma.admin.common.BizException;
import com.sma.admin.config.LegacyBrandSource;
import com.sma.admin.tenant.internal.service.*;

class LegacyBrandConverterTests {
    private final LegacyBrandConverter converter=new LegacyBrandConverter(new BrandValidation(new LoginTemplates()));
    private static LegacyBrandSource.Value value(String text) { return new LegacyBrandSource.Value(text,true,false); }
    private static LegacyBrandSource.Snapshot source(Map<LegacyBrandSource.Key,LegacyBrandSource.Value> values) { return new LegacyBrandSource.Snapshot("test-revision",values); }

    @Test void preservesPreviouslyPublicIdentityLegalDataAndBrowserBackgroundWithoutInventingAssetIds() {
        var converted=converter.convert(source(Map.of(NAME,value("客户系统"),COPYRIGHT_OWNER,value("客户公司"),COPYRIGHT_URL,value("https://customer.example.com"),
                ICP_RECORD,value("测试备案号"),ICP_URL,value("https://beian.miit.gov.cn"),BACKGROUND_URL,value("/images/old-background.png"),BACKGROUND_BLUR,value("true"))),"Tenant");
        assertThat(converted.content().identity().systemName()).isEqualTo("客户系统");
        assertThat(converted.content().legal().copyrightOwner()).isEqualTo("客户公司");
        assertThat(converted.content().legal().icpRecord()).isEqualTo("测试备案号");
        assertThat(converted.legacyBackgroundUrl()).isEqualTo("/images/old-background.png");
        assertThat(converted.content().login().backgroundBlur()).isTrue();assertThat(converted.content().assets()).isEmpty();
        assertThat(converted.excludedKeys()).isEmpty();
        assertThat(converter.convert(source(Map.of(BACKGROUND_URL,value("https://customer.example.com/background.png"))),"Tenant").legacyBackgroundUrl())
                .isEqualTo("https://customer.example.com/background.png");
    }

    @Test void secretAndNonPublicValuesCannotBecomePublicBrandContent() {
        var secret=new LegacyBrandSource.Value("never-publish-secret",true,true);assertThat(secret.text()).isNull();
        var converted=converter.convert(source(Map.of(NAME,secret,COPYRIGHT_OWNER,new LegacyBrandSource.Value("private-owner",false,false))),"Current tenant");
        assertThat(converted.content().identity().systemName()).isEqualTo("Current tenant");assertThat(converted.content().legal().copyrightOwner()).isEmpty();
        assertThat(converted.excludedKeys()).containsExactlyInAnyOrder(NAME,COPYRIGHT_OWNER);
        assertThat(converted.toString()).doesNotContain("never-publish-secret","private-owner");
    }

    @Test void unsafeBackgroundInvalidBooleanAndIncompleteLegalFieldsAreRejectedWithoutSilentTruncation() {
        for(String url:java.util.List.of("//evil.example.com/a","javascript:alert(1)","file:///tmp/a","/../private.png","/%2e%2e/private.png","https://user:password@example.com/a"))
            assertThatThrownBy(()->converter.convert(source(Map.of(BACKGROUND_URL,value(url))),"Tenant")).isInstanceOf(BizException.class);
        assertThatThrownBy(()->converter.convert(source(Map.of(BACKGROUND_BLUR,value("yes"))),"Tenant")).isInstanceOf(BizException.class);
        assertThatThrownBy(()->converter.convert(source(Map.of(ICP_URL,value("https://example.com"))),"Tenant")).isInstanceOf(BizException.class);
        assertThatThrownBy(()->converter.convert(source(Map.of(NAME,value("x".repeat(129)))),"Tenant")).isInstanceOf(BizException.class);
    }
    @Test void migrationFailureIdentifiesFieldsWithoutPrintingTheirValues() {
        assertThatThrownBy(()->converter.convert(source(Map.of(BACKGROUND_URL,value("javascript:private-token"))),"Tenant"))
                .isInstanceOf(BizException.class).hasMessageContaining("login.backgroundUrl").hasMessageNotContaining("private-token");
    }
}
