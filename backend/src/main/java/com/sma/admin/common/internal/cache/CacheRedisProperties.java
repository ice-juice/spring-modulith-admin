package com.sma.admin.common.internal.cache;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Explicit supported Spring Redis connection keys; secrets must never appear in toString/health/governance output. */
@ConfigurationProperties(prefix="spring.data.redis", ignoreUnknownFields=false)
public record CacheRedisProperties(String host, Integer port, Integer database, String username, String password,
        Duration timeout, Duration connectTimeout, Ssl ssl, Sentinel sentinel, Cluster cluster) {
    public record Ssl(boolean enabled) {}
    public record Sentinel(String master, List<String> nodes, String username, String password) {
        public Sentinel {
            if (master == null || !master.matches("[a-zA-Z0-9_-]{1,128}")) throw new IllegalArgumentException("Invalid Sentinel master");
            nodes = validateNodes(nodes);
        }
        @Override public String toString() { return "Sentinel[credentials=redacted]"; }
    }
    public record Cluster(List<String> nodes, Integer maxRedirects) {
        public Cluster {
            nodes = validateNodes(nodes); maxRedirects = maxRedirects == null ? 3 : maxRedirects;
            if (maxRedirects < 1 || maxRedirects > 10) throw new IllegalArgumentException("Invalid Cluster redirect limit");
        }
    }
    public CacheRedisProperties {
        host = host == null ? "127.0.0.1" : host;
        port = port == null ? 6379 : port; database = database == null ? 0 : database;
        timeout = timeout == null ? Duration.ofMillis(200) : timeout;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(1) : connectTimeout;
        ssl = ssl == null ? new Ssl(false) : ssl;
        if (host.isBlank() || host.contains("/") || host.contains("@") || host.chars().anyMatch(Character::isWhitespace)
                || port < 1 || port > 65535 || database < 0 || database > 15 || (sentinel != null && cluster != null)
                || (cluster != null && database != 0) || timeout.compareTo(Duration.ofMillis(10)) < 0
                || timeout.compareTo(Duration.ofSeconds(5)) > 0 || connectTimeout.compareTo(Duration.ofMillis(10)) < 0
                || connectTimeout.compareTo(Duration.ofSeconds(10)) > 0) throw new IllegalArgumentException("Invalid Redis connection configuration");
    }
    private static List<String> validateNodes(List<String> nodes) {
        if (nodes == null || nodes.isEmpty() || nodes.size() > 32) throw new IllegalArgumentException("Redis topology requires 1 to 32 endpoints");
        for (String node : nodes) {
            try {
                URI uri = URI.create("redis://" + node);
                if (uri.getHost() == null || uri.getPort() < 1 || uri.getPort() > 65535 || uri.getUserInfo() != null
                        || !uri.getPath().isEmpty() || uri.getQuery() != null || uri.getFragment() != null)
                    throw new IllegalArgumentException();
            } catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Invalid Redis topology endpoint"); }
        }
        return List.copyOf(nodes);
    }
    @Override public String toString() { return "CacheRedisProperties[topology=" + (cluster != null ? "cluster" : sentinel != null ? "sentinel" : "standalone") + ",credentials=redacted]"; }
}
