package com.sma.admin.common.internal.cache;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class CacheNodeIdentityTests {
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    @org.springframework.boot.context.properties.EnableConfigurationProperties(CacheNodeProperties.class)
    static class Binding {}
    @Test void overloadedPropertiesBindAsARealConfigurationBean(){
        new org.springframework.boot.test.context.runner.ApplicationContextRunner().withUserConfiguration(Binding.class)
                .withPropertyValues("sma.cache.nodes.id=a","sma.cache.nodes.expected=b,a","sma.cache.nodes.version=2")
                .run(context->{assertThat(context).hasNotFailed();var properties=context.getBean(CacheNodeProperties.class);
                    assertThat(properties.version()).isEqualTo(2);assertThat(properties.expected()).containsExactly("a","b");});
    }
    @Test void manifestIsCanonicalButEachProcessHasItsOwnLifetime(){
        var first=new CacheNodeIdentity(new CacheNodeProperties("test","a",List.of("b","a"),true));
        var second=new CacheNodeIdentity(new CacheNodeProperties("test","b",List.of("a","b"),true));
        assertThat(first.manifestHash()).isEqualTo(second.manifestHash());assertThat(first.processId()).isNotEqualTo(second.processId());
        assertThat(first.clusterHash()).matches("[0-9a-f]{64}");assertThat(first.properties().expected()).containsExactly("a","b");
        assertThatThrownBy(()->first.properties().expected().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void nodeListsAreExplicitUniqueAndBounded(){
        assertThat(new CacheNodeProperties(null,null,null,null).expected()).containsExactly("local");
        assertThatThrownBy(()->new CacheNodeProperties("test","a",List.of("a","a"),true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new CacheNodeProperties("test","a",List.of("b"),true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new CacheNodeProperties("test","a",List.of(),true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new CacheNodeProperties("test","a",java.util.stream.IntStream.range(0,33).mapToObj(i->"a"+i).toList(),true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new CacheNodeProperties("test","a\n",null,true)).isInstanceOf(IllegalArgumentException.class);
    }
}
