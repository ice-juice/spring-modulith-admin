package com.sma.admin.mail.internal.service;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Deployment egress policy, independent of each tenant's SMTP credentials. No TLS downgrade switches. */
@ConfigurationProperties("sma.mail.smtp")
public record SmtpProperties(List<Route> routes, Duration connectTimeout, Duration readTimeout,
                             Duration writeTimeout, Duration operationTimeout, int maximumConcurrent) {
    public record Route(String host, Set<Integer> ports, Set<String> approvedPrivateAddresses) {
        public Route {
            if(host==null || !host.matches("[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?") || host.contains(".."))
                throw new IllegalArgumentException("Invalid SMTP route host");
            ports=ports==null?Set.of(465,587):Set.copyOf(ports);
            approvedPrivateAddresses=approvedPrivateAddresses==null?Set.of():Set.copyOf(approvedPrivateAddresses);
            if(ports.isEmpty() || ports.size()>10 || ports.stream().anyMatch(p->p<1 || p>65535) || approvedPrivateAddresses.size()>16)
                throw new IllegalArgumentException("Invalid SMTP route bounds");
            if(approvedPrivateAddresses.stream().anyMatch(s->!s.matches("[0-9a-fA-F:.]{2,45}")))
                throw new IllegalArgumentException("SMTP approved private address must be a literal IP");
        }
    }
    public SmtpProperties {
        routes=routes==null?List.of():List.copyOf(routes);
        connectTimeout=connectTimeout==null?Duration.ofSeconds(5):connectTimeout;
        readTimeout=readTimeout==null?Duration.ofSeconds(10):readTimeout;
        writeTimeout=writeTimeout==null?Duration.ofSeconds(10):writeTimeout;
        operationTimeout=operationTimeout==null?Duration.ofSeconds(40):operationTimeout;
        maximumConcurrent=maximumConcurrent==0?4:maximumConcurrent;
        if(routes.size()>100 || routes.stream().map(Route::host).distinct().count()!=routes.size()
                || maximumConcurrent<1 || maximumConcurrent>32)throw new IllegalArgumentException("Invalid SMTP capacity");
        for(Duration d:List.of(connectTimeout,readTimeout,writeTimeout,operationTimeout))
            if(d.compareTo(Duration.ofMillis(100))<0 || d.compareTo(Duration.ofSeconds(60))>0)throw new IllegalArgumentException("Invalid SMTP timeout");
    }
}
