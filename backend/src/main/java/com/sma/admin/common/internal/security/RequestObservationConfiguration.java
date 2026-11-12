package com.sma.admin.common.internal.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods=false)
class RequestObservationConfiguration implements WebMvcConfigurer {
    private final RequestObservationInterceptor interceptor;
    RequestObservationConfiguration(RequestObservationInterceptor interceptor){this.interceptor=interceptor;}
    @Override public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(interceptor).order(Ordered.HIGHEST_PRECEDENCE+100);}
}
