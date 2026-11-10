package com.sma.admin.openapi.internal;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.sma.admin.common.*;
import com.sma.admin.openapi.internal.service.OpenAuthenticationService;
@Configuration(proxyBeanMethods=false)
class OpenSecurityConfiguration {
 @Bean @Order(1) SecurityFilterChain openSecurity(HttpSecurity http,OpenAuthenticationService authentication,ErrorResponseWriter errors)throws Exception{
  return http.securityMatcher("/open/**").csrf(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable)
   .sessionManagement(session->session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .exceptionHandling(ex->ex.authenticationEntryPoint((request,response,failure)->errors.write(request,response,CommonErrorCode.UNAUTHORIZED)).accessDeniedHandler((request,response,failure)->errors.write(request,response,CommonErrorCode.FORBIDDEN)))
   .authorizeHttpRequests(authorize->authorize.requestMatchers("/open/token").permitAll().anyRequest().authenticated())
   .addFilterBefore(new OpenClientAuthenticationFilter(authentication,errors),UsernamePasswordAuthenticationFilter.class).build();
 }
}
