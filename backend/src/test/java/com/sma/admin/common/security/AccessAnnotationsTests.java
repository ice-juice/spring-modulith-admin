package com.sma.admin.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.util.SimpleMethodInvocation;

/**
 * 验证访问控制元注解在 Spring Security 的模板表达式下按约定生效。
 */
class AccessAnnotationsTests {

	private final PreAuthorizeAuthorizationManager manager = new PreAuthorizeAuthorizationManager();

	AccessAnnotationsTests() {
		manager.setTemplateDefaults(new AnnotationTemplateExpressionDefaults());
	}

	@Test
	void requiresPermissionGrantsOnlyTheNamedAuthority() throws Exception {
		assertThat(granted(user("iam:user:create"), "create")).isTrue();
		assertThat(granted(user("iam:user:view"), "create")).isFalse();
		assertThat(granted(anonymous(), "create")).isFalse();
	}

	@Test
	void permitLoggedInRejectsAnonymousCallers() throws Exception {
		assertThat(granted(user(), "profile")).isTrue();
		assertThat(granted(anonymous(), "profile")).isFalse();
	}

	@Test
	void unrecognizedPrincipalCannotGainHumanAccessWithAnAuthority() throws Exception {
		var forged = UsernamePasswordAuthenticationToken.authenticated("tester", null,
				AuthorityUtils.createAuthorityList("iam:user:create"));
		assertThat(granted(forged, "profile")).isFalse();
		assertThat(granted(forged, "create")).isFalse();
	}

	@Test
	void permitAnonymousAllowsEveryone() throws Exception {
		assertThat(granted(anonymous(), "captcha")).isTrue();
	}

	private boolean granted(Authentication authentication, String methodName) throws Exception {
		Method method = SampleEndpoints.class.getDeclaredMethod(methodName);
		MethodInvocation invocation = new SimpleMethodInvocation(new SampleEndpoints(), method);
		AuthorizationResult result = manager.authorize(() -> authentication, invocation);
		return result != null && result.isGranted();
	}

	private static Authentication user(String... authorities) {
		var principal = new SmaPrincipal(101, "tester", 1, "admin", null, false,
				DataAccess.none(), java.util.Set.of(authorities), "UTC");
		return UsernamePasswordAuthenticationToken.authenticated(principal, null, AuthorityUtils.createAuthorityList(authorities));
	}

	private static Authentication anonymous() {
		return new AnonymousAuthenticationToken("key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
	}

	static class SampleEndpoints {

		@RequiresPermission("iam:user:create")
		void create() {
		}

		@PermitLoggedIn
		void profile() {
		}

		@PermitAnonymous
		void captcha() {
		}

	}

}
