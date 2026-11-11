package com.sma.admin.common.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.sma.admin.support.IntegrationTest;

@IntegrationTest
@AutoConfigureMockMvc
class I18nWebTests {

	@Autowired
	MockMvcTester mvc;

    @org.junit.jupiter.api.BeforeEach
    void useRecognizedHumanIdentity(org.junit.jupiter.api.TestInfo test) {
        if (test.getTestMethod().orElseThrow().isAnnotationPresent(WithMockUser.class)) {
            var principal = com.sma.admin.support.Users.principal(101, 1,
                    com.sma.admin.common.security.DataAccess.none());
            var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
            context.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                    .authenticated(principal, null, java.util.List.of()));
            org.springframework.security.test.context.TestSecurityContextHolder.setContext(context);
        }
    }

	@Test
	@WithMockUser
	void businessErrorsUseTheDefaultLanguageWithoutHeader() {
		assertThat(mvc.get().uri("/api/test-probe/biz")).hasStatusOk().bodyJson()
				.hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo(400))
				.hasPathSatisfying("$.error", v -> v.assertThat().isEqualTo("common.error.data-not-found"))
				.hasPathSatisfying("$.message", v -> v.assertThat().isEqualTo("数据不存在或已被删除"));
	}

    @Test @WithMockUser
    void cacheBackpressureIsARealLocalized503RatherThanASuccessResponse() {
        assertThat(mvc.get().uri("/api/test-probe/cache-busy")).hasStatus(503).bodyJson()
                .hasPathSatisfying("$.error", value -> value.assertThat().isEqualTo("common.error.unavailable"))
                .hasPathSatisfying("$.message", value -> value.assertThat().isEqualTo("服务暂时繁忙，请稍后重试"));
        assertThat(mvc.get().uri("/api/test-probe/cache-busy").header("Accept-Language", "en-US")).hasStatus(503).bodyJson()
                .hasPathSatisfying("$.message", value -> value.assertThat().isEqualTo("The service is temporarily busy; please try again later"));
    }

	@Test
	@WithMockUser
	void businessErrorsFollowAcceptLanguage() {
		assertThat(mvc.get().uri("/api/test-probe/biz").header("Accept-Language", "en-US")).bodyJson()
				.hasPathSatisfying("$.message", v -> v.assertThat().isEqualTo("The data does not exist or has been deleted"));
		assertThat(mvc.get().uri("/api/test-probe/biz").header("Accept-Language", "en-GB,en;q=0.8")).bodyJson()
				.hasPathSatisfying("$.message", v -> v.assertThat().isEqualTo("The data does not exist or has been deleted"));
		assertThat(mvc.get().uri("/api/test-probe/biz").header("Accept-Language", "fr-FR")).bodyJson()
				.hasPathSatisfying("$.message", v -> v.assertThat().isEqualTo("数据不存在或已被删除"));
		assertThat(mvc.get().uri("/api/test-probe/duplicate").header("Accept-Language", "en-US")).bodyJson()
				.hasPathSatisfying("$.message", v -> v.assertThat().isEqualTo("The data already exists: admin"));
	}

	@Test
	@WithMockUser
	void validationMessagesAreLocalized() {
		assertThat(mvc.post().uri("/api/test-probe/validate").header("Accept-Language", "en-US")
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
				.hasStatus(HttpStatus.BAD_REQUEST).bodyJson()
				.hasPathSatisfying("$.error", v -> v.assertThat().isEqualTo("common.error.validation-failed"))
				.hasPathSatisfying("$.data[0].field", v -> v.assertThat().isEqualTo("name"))
				.hasPathSatisfying("$.data[0].message", v -> v.assertThat().isEqualTo("must not be blank"));
		assertThat(mvc.post().uri("/api/test-probe/validate")
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
				.bodyJson().hasPathSatisfying("$.data[0].message", v -> v.assertThat().isEqualTo("不能为空"));
	}

	@Test
	void unauthenticatedRequestsGetALocalized401() {
		assertThat(mvc.get().uri("/api/test-probe/biz").header("Accept-Language", "en-US"))
				.hasStatus(HttpStatus.UNAUTHORIZED).bodyJson()
				.hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo(401))
				.hasPathSatisfying("$.error", v -> v.assertThat().isEqualTo("common.error.unauthorized"))
				.hasPathSatisfying("$.message", v -> v.assertThat().isEqualTo("Please sign in first"));
	}

	@Test
	@WithMockUser
	void requestTimeZoneAndInstantFormat() {
		assertThat(mvc.get().uri("/api/test-probe/context").header("X-Time-Zone", "America/New_York")
				.header("Accept-Language", "en-US")).bodyJson()
				.hasPathSatisfying("$.data.zone", v -> v.assertThat().isEqualTo("America/New_York"))
				.hasPathSatisfying("$.data.locale", v -> v.assertThat().isEqualTo("en-US"))
				.hasPathSatisfying("$.data.instant", v -> v.assertThat().isEqualTo("2026-11-10T03:00:00Z"));
		assertThat(mvc.get().uri("/api/test-probe/context").header("X-Time-Zone", "Not/AZone")).bodyJson()
				.hasPathSatisfying("$.data.zone", v -> v.assertThat().isEqualTo("Asia/Shanghai"))
				.hasPathSatisfying("$.data.locale", v -> v.assertThat().isEqualTo("zh-CN"));
	}

}
