package com.sma.admin.auth.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.security.web.SecurityFilterChain;

import com.sma.admin.common.CommonErrorCode;
import com.sma.admin.common.ErrorResponseWriter;
import com.sma.admin.auth.internal.service.SessionService;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security baseline of the stateless REST backend: deny by default, URL level requires authentication, method level
 * permissions come from the common.security annotations. 401/403 bodies are localized {@code ApiResponse} JSON.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfiguration {

	/**
	 * Enables the {@code {value}} placeholder in meta annotations such as {@code @RequiresPermission("iam:user:create")}.
	 */
	@Bean
	static AnnotationTemplateExpressionDefaults annotationTemplateExpressionDefaults() {
		return new AnnotationTemplateExpressionDefaults();
	}

	@Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, ErrorResponseWriter errors, SessionService sessions) throws Exception {
		return http
				.csrf(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint((request, response, e) -> errors.write(request, response, CommonErrorCode.UNAUTHORIZED))
						.accessDeniedHandler((request, response, e) -> errors.write(request, response, CommonErrorCode.FORBIDDEN)))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/actuator/health/**", "/actuator/info", "/error").permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/captcha", "/api/config/parameters/public").permitAll()
                        // Handshake validates a one-use ticket bound to a live session before upgrade.
                        .requestMatchers("/api/message/ws").permitAll()
						.anyRequest().authenticated())
                .addFilterBefore(new TokenAuthenticationFilter(sessions, errors), UsernamePasswordAuthenticationFilter.class)
				.build();
	}

}
