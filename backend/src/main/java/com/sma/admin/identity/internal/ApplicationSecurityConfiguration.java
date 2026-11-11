package com.sma.admin.identity.internal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.csrf.*;
import com.sma.admin.common.*;
import com.sma.admin.identity.internal.service.*;

@Configuration(proxyBeanMethods=false) @EnableConfigurationProperties(IdentityHttpProperties.class)
class ApplicationSecurityConfiguration {
    @Bean @Order(2)
    SecurityFilterChain applicationSecurity(HttpSecurity http,ApplicationClientResolver clients,IdentitySessionStore sessions,ErrorResponseWriter errors,IdentityHttpProperties settings)throws Exception{
        var csrfHandler=new CsrfTokenRequestAttributeHandler(){
            @Override public String resolveCsrfTokenValue(HttpServletRequest request,CsrfToken token){return ApplicationHttp.header(request,ApplicationHttp.CSRF_HEADER);}
        };
        return http.securityMatcher("/app/**").httpBasic(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf->csrf.csrfTokenRepository(new ApplicationCsrfRepository()).csrfTokenRequestHandler(csrfHandler)
                        // Restoring an existing application session is not a new login. Identity login/refresh owns CSRF rotation.
                        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy())
                        .requireCsrfProtectionMatcher(request->ApplicationHttp.client(request).web() && CsrfFilter.DEFAULT_CSRF_MATCHER.matches(request)))
                .exceptionHandling(ex->ex.authenticationEntryPoint((request,response,failure)->errors.write(request,response,CommonErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((request,response,failure)->errors.write(request,response,CommonErrorCode.FORBIDDEN)))
                .authorizeHttpRequests(auth->auth.requestMatchers(HttpMethod.GET,"/app/identity/bootstrap").permitAll().requestMatchers(HttpMethod.HEAD,"/app/identity/bootstrap").permitAll()
                        .requestMatchers(HttpMethod.POST,"/app/identity/google/transactions","/app/identity/google/completions","/app/identity/sessions/refresh",
                                "/app/identity/email/challenges","/app/identity/email/verifications","/app/identity/registrations","/app/identity/password/resets","/app/identity/password/sessions").permitAll().anyRequest().authenticated())
                .addFilterBefore(new ApplicationRequestFilter(clients,errors,settings),CsrfFilter.class)
                .addFilterBefore(new ApplicationAuthenticationFilter(sessions,errors),UsernamePasswordAuthenticationFilter.class).build();
    }
}
