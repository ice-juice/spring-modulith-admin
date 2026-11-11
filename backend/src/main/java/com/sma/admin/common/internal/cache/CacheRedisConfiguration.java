package com.sma.admin.common.internal.cache;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Properties;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="sma.cache.provider", havingValue="redis")
@EnableConfigurationProperties(CacheRedisProperties.class)
class CacheRedisConfiguration {
    @Bean LettuceConnectionFactory governedRedisConnectionFactory(CacheRedisProperties properties) {
        var sockets = SocketOptions.builder().connectTimeout(properties.connectTimeout()).build();
        ClientOptions options = properties.cluster() == null
                ? ClientOptions.builder().socketOptions(sockets).disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS).build()
                : ClusterClientOptions.builder().socketOptions(sockets).disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                    .topologyRefreshOptions(ClusterTopologyRefreshOptions.builder().enableAllAdaptiveRefreshTriggers().enablePeriodicRefresh(Duration.ofSeconds(30)).build()).build();
        var client = LettuceClientConfiguration.builder().commandTimeout(properties.timeout()).shutdownTimeout(Duration.ZERO).clientOptions(options);
        if (properties.ssl().enabled()) client.useSsl();
        RedisConfiguration config;
        if (properties.cluster() != null) {
            var cluster = new RedisClusterConfiguration(properties.cluster().nodes());
            cluster.setMaxRedirects(properties.cluster().maxRedirects());
            cluster.setUsername(properties.username()); cluster.setPassword(RedisPassword.of(properties.password())); config = cluster;
        } else if (properties.sentinel() != null) {
            var sentinel = new RedisSentinelConfiguration(properties.sentinel().master(), new LinkedHashSet<>(properties.sentinel().nodes()));
            sentinel.setDatabase(properties.database()); sentinel.setUsername(properties.username()); sentinel.setPassword(RedisPassword.of(properties.password()));
            sentinel.setSentinelUsername(properties.sentinel().username()); sentinel.setSentinelPassword(RedisPassword.of(properties.sentinel().password())); config = sentinel;
        } else {
            var standalone = new RedisStandaloneConfiguration(properties.host(), properties.port());
            standalone.setDatabase(properties.database()); standalone.setUsername(properties.username()); standalone.setPassword(RedisPassword.of(properties.password())); config = standalone;
        }
        return new LettuceConnectionFactory(config, client.build());
    }
    @Bean StringRedisTemplate governedRedisTemplate(LettuceConnectionFactory governedRedisConnectionFactory, CacheRedisProperties properties) {
        var template = new StringRedisTemplate(governedRedisConnectionFactory);
        try (var connection = governedRedisConnectionFactory.getConnection()) {
            if (!"PONG".equals(connection.ping())) throw new IllegalStateException("Redis startup health check failed");
            if (properties.cluster() == null) requireBoundedMemory(connection.serverCommands().info("memory"));
        }
        if (properties.cluster() != null) try (var cluster = governedRedisConnectionFactory.getClusterConnection()) {
            for (var node : cluster.clusterGetNodes()) requireBoundedMemory(cluster.serverCommands().info(node, "memory"));
        }
        return template;
    }
    static void requireBoundedMemory(Properties memory) {
        if (memory == null || Long.parseLong(memory.getProperty("maxmemory", "0")) <= 0)
            throw new IllegalStateException("Configure a positive Redis maxmemory limit before enabling shared caching");
    }
}
