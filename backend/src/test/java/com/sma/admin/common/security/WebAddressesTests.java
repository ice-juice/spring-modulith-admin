package com.sma.admin.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class WebAddressesTests {
    @Test void localAssetsCannotTurnIntoExternalOrScriptDestinations() {
        for (String value : new String[]{"//evil.example/img", "/\\evil.example/img", "javascript:alert(1)", "/a/../b", "/%0d%0aevil", " /img"})
            assertThat(WebAddresses.localResource(value)).isEmpty();
        assertThat(WebAddresses.localResource("/assets/logo.png?v=2")).contains("/assets/logo.png?v=2");
    }
    @Test void linksRejectCredentialsAndBrowserParserAmbiguities() {
        for (String value : new String[]{"https://trusted@evil.example", "https://example\\@evil.example", "javascript:alert(1)", "data:text/html,x", "https://example:99999", "https://example/%0a"})
            assertThat(WebAddresses.httpLink(value)).isEmpty();
        assertThat(WebAddresses.httpLink("https://example/help?q=hello%20world")).isPresent();
        assertThat(WebAddresses.httpLink("https://example/help?q=hello")).isPresent();
        assertThat(WebAddresses.origin("https://EXAMPLE:443")).contains("https://example");
        assertThat(WebAddresses.origin("https://example?x=1")).isEmpty();
    }
}
