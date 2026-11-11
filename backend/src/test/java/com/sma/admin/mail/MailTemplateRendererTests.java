package com.sma.admin.mail;

import static org.assertj.core.api.Assertions.*;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.sma.admin.common.BizException;
import com.sma.admin.mail.internal.service.MailTemplateRenderer;

class MailTemplateRendererTests {
    private final MailTemplateRenderer renderer = new MailTemplateRenderer();
    private MailTemplateContent.Variable text = new MailTemplateContent.Variable(MailTemplateContent.Type.TEXT, 128, true);
    private MailTemplateContent content(String subject, String body, boolean html) {
        return new MailTemplateContent(subject, body, html, Map.of("name", text), Set.of());
    }
    @Test void textIsLiteralAndHtmlVariablesCannotCreateElementsOrAttributes() {
        var plain = renderer.render(content("Hello {{name}}", "{{name}}", false), Map.of("name", "$1\\test"));
        assertThat(plain.body()).isEqualTo("$1\\test");
        var html = renderer.render(content("Notice", "<p>{{name}}</p>", true), Map.of("name", "<img src=x onerror=alert(1)>"));
        assertThat(html.body()).doesNotContain("<img").contains("&lt;img");
        assertThat(html.toString()).doesNotContain("img");
    }
    @Test void onlyTypedHttpsLinksToThePublishedAllowlistAreInterpolated() {
        var link = new MailTemplateContent.Variable(MailTemplateContent.Type.HTTPS_URL, 1000, true);
        var template = new MailTemplateContent("Reset", "<p><a href=\"{{link}}\">Reset</a></p>", true, Map.of("link", link), Set.of("app.example.test"));
        assertThat(renderer.render(template, Map.of("link", "https://app.example.test/reset?token=a&x=b")).body())
                .contains("https://app.example.test/reset?token=a&amp;x=b");
        for (String value : new String[]{"javascript:alert(1)", "http://app.example.test/a", "https://app.example.test.evil.test/a",
                "https://user@app.example.test/a", "https://evil.test/a", "https://app.example.test:444/a"}) {
            assertThatThrownBy(() -> renderer.render(template, Map.of("link", value))).isInstanceOf(BizException.class);
        }
    }
    @Test void activeContentUnsafeAttributesAndTemplateExpressionsAreRejected() {
        for (String body : new String[]{"<script>alert(1)</script>{{name}}", "<p onclick=\"alert(1)\">{{name}}</p>",
                "<img src=\"https://tracker.test/p\">{{name}}", "<style>body{}</style>{{name}}", "${T(java.lang.Runtime)}{{name}}",
                "<a href=\"https://evil.test\">{{name}}</a>", "<a href=\"{{name}}\">click</a>", "<p title=\"{{name}}\">x</p>"}) {
            assertThatThrownBy(() -> renderer.validate(content("Notice", body, true))).isInstanceOf(BizException.class);
        }
    }
    @Test void schemaUnknownVariablesMissingValuesAndHeaderInjectionAreRejected() {
        var template = content("Hi {{name}}", "Hello", false);
        assertThatThrownBy(() -> renderer.render(template, Map.of())).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> renderer.render(template, Map.of("name", "A", "extra", "B"))).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> renderer.render(template, Map.of("name", "A\r\nBcc: victim@example.test"))).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> renderer.render(template, Map.of("name", "a".repeat(129)))).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> renderer.validate(content("Hello", "{{unknown}}", false))).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> renderer.validate(content("Hello", "{{name.toString()}}", false))).isInstanceOf(BizException.class);
    }
    @Test void numericVariablesAreBoundedStringsWithoutFloatingPointLoss() {
        var template = new MailTemplateContent("Order", "{{id}} / {{price}}", false, Map.of(
                "id", new MailTemplateContent.Variable(MailTemplateContent.Type.INTEGER, 30, true),
                "price", new MailTemplateContent.Variable(MailTemplateContent.Type.DECIMAL, 30, true)), Set.of());
        assertThat(renderer.render(template, Map.of("id", "999999999999999999", "price", "123.45678901")).body())
                .isEqualTo("999999999999999999 / 123.45678901");
        assertThatThrownBy(() -> renderer.render(template, Map.of("id", "1e9", "price", "NaN"))).isInstanceOf(BizException.class);
    }
    @Test void optionalTextIsEmptyAndRenderedByteBudgetIsEnforced() {
        var template = new MailTemplateContent("Notice", "{{note}}", false,
                Map.of("note", new MailTemplateContent.Variable(MailTemplateContent.Type.TEXT, 4096, false)), Set.of());
        assertThat(renderer.render(template, Map.of()).body()).isEmpty();
        var repeated = new MailTemplateContent("Notice", "{{note}}".repeat(20), false, template.variables(), Set.of());
        assertThatThrownBy(() -> renderer.render(repeated, Map.of("note", "汉".repeat(4096)))).isInstanceOf(BizException.class);
    }
}
