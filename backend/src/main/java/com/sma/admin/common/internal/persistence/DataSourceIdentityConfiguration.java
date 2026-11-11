package com.sma.admin.common.internal.persistence;

import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import com.sma.admin.common.persistence.DataSourceIdentity;

@Configuration(proxyBeanMethods = false)
class DataSourceIdentityConfiguration {
    /** Boot's platform pool only. Future business pools are decorated at creation, before routing. */
    @Bean static BeanPostProcessor platformDataSourceIdentity(Environment environment) {
        var identity = new DataSourceIdentity(environment.getProperty("sma.persistence.resource-id", "platform"),
                environment.getProperty("sma.persistence.resource-revision", Long.class, 1L));
        return new BeanPostProcessor() {
            @Override public Object postProcessAfterInitialization(Object bean, String name) {
                if (name.equals("dataSource") && bean instanceof DataSource source && !(bean instanceof IdentifiedDataSource)) {
                    return new IdentifiedDataSource(source, identity);
                }
                return bean;
            }
        };
    }
}
