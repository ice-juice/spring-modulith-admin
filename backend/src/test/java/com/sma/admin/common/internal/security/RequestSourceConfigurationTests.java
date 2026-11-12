package com.sma.admin.common.internal.security;

import static org.assertj.core.api.Assertions.*;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

class RequestSourceConfigurationTests {
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    @org.springframework.boot.context.properties.EnableConfigurationProperties(RequestSourceProperties.class)
    static class Binding {}
    @Test void applicationBeanDefaultsAndOverridesAreValidated(){
        var runner=new org.springframework.boot.test.context.runner.ApplicationContextRunner().withUserConfiguration(Binding.class);
        runner.run(context->{
            assertThat(context).hasNotFailed();var p=context.getBean(RequestSourceProperties.class);
            assertThat(p.trustedProxies()).isEmpty();assertThat(p.maximumHops()).isEqualTo(16);assertThat(p.maximumHeaderCharacters()).isEqualTo(2048);
        });
        runner.withPropertyValues("sma.security.source.trusted-proxies=192.0.2.0/24,2001:DB8::/32","sma.security.source.maximum-hops=8")
                .run(context->{assertThat(context).hasNotFailed();assertThat(context.getBean(RequestSourceProperties.class).trustedProxies()).containsExactlyInAnyOrder("192.0.2.0/24","2001:db8:0:0:0:0:0:0/32");});
        for(String value:new String[]{"sma.security.source.trusted-proxies=0.0.0.0/0","sma.security.source.trusted-proxies=example.com",
                "sma.security.source.maximum-hops=0","sma.security.source.maximum-header-characters=9000"})
            runner.withPropertyValues(value).run(context->assertThat(context).hasFailed());
        assertThatThrownBy(()->new RequestSourceProperties(java.util.stream.IntStream.range(1,66).mapToObj(i->"192.0.2."+i).collect(java.util.stream.Collectors.toSet()),null,null))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void documentedEnvironmentBindsAndDoesNotExpandTenantHostTrust()throws Exception{
        var environment=new StandardEnvironment();environment.getPropertySources().addFirst(new MapPropertySource("source-test",Map.of(
                "SMA_SECURITY_SOURCE_TRUSTED_PROXIES","192.0.2.0/24","SMA_SECURITY_SOURCE_MAXIMUM_HOPS","4",
                "SMA_SECURITY_SOURCE_MAXIMUM_HEADER_CHARACTERS","512")));
        for(var source:new YamlPropertySourceLoader().load("application",new ClassPathResource("application.yml")))environment.getPropertySources().addLast(source);
        var p=Binder.get(environment).bind("sma.security.source",Bindable.of(RequestSourceProperties.class)).get();
        assertThat(p.trustedProxies()).containsExactly("192.0.2.0/24");assertThat(p.maximumHops()).isEqualTo(4);assertThat(p.maximumHeaderCharacters()).isEqualTo(512);
        assertThat(Binder.get(environment).bind("sma.tenant.entry",Bindable.of(TenantIngressProperties.class)).orElseGet(()->new TenantIngressProperties(Set.of())).trustedProxyAddresses()).isEmpty();
        var filter=new RequestSourceFilter(p,new RequestSourceScope());
        assertThatThrownBy(()->filter.setEnvironment(new org.springframework.mock.env.MockEnvironment().withProperty("server.forward-headers-strategy","native")))
                .isInstanceOf(IllegalStateException.class);
    }
}
