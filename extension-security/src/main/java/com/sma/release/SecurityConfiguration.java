package com.sma.release;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;

@Configuration(proxyBeanMethods=false)
public class SecurityConfiguration {
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean TransactionTemplate transactions(PlatformTransactionManager manager) { return new TransactionTemplate(manager); }
    @Bean SecurityFilterChain releaseSecurity(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable()).httpBasic(basic -> basic.disable()).formLogin(form -> form.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // APIs use opaque bearer sessions checked by IdentityService in each transaction, never cookies.
                .authorizeHttpRequests(auth -> auth.requestMatchers("/release/v1/**").permitAll().anyRequest().denyAll())
                .headers(headers -> headers.cacheControl(cache -> {}).contentTypeOptions(type -> {}))
                .build();
    }
}
