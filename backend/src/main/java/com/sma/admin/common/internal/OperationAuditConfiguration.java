package com.sma.admin.common.internal;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
@Configuration(proxyBeanMethods=false)
class OperationAuditConfiguration implements WebMvcConfigurer {
 private final OperationAuditInterceptor interceptor;
 OperationAuditConfiguration(OperationAuditInterceptor interceptor){this.interceptor=interceptor;}
 @Override public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(interceptor).addPathPatterns("/api/**");}
}
