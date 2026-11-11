package com.sma.admin.extension.internal.service;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties({ExtensionProperties.class,ConsoleProxyProperties.class})
public class ExtensionConfiguration {
    @Bean(destroyMethod="shutdown")
    @ConditionalOnProperty(name="sma.extension.enabled",havingValue="true")
    ThreadPoolTaskScheduler extensionControlScheduler(ExtensionRuntimeService service) {
        var scheduler=new ThreadPoolTaskScheduler();scheduler.setPoolSize(1);scheduler.setThreadNamePrefix("extension-control-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);scheduler.setAwaitTerminationSeconds(35);scheduler.initialize();
        scheduler.scheduleWithFixedDelay(service::synchronize,Duration.ofSeconds(30));return scheduler;
    }
}
