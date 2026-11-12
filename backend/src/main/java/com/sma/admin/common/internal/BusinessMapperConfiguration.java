package com.sma.admin.common.internal;

import static com.sma.admin.SpringbootModulithAdminApplication.BUSINESS_PACKAGE;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.sma.admin.common.internal.persistence.BusinessRoutingDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Static business mappers use a distinct session factory and require a pinned route. */
@Configuration(proxyBeanMethods=false)
@MapperScan(basePackages=BUSINESS_PACKAGE,sqlSessionFactoryRef="businessSqlSessionFactory",
        annotationClass=org.apache.ibatis.annotations.Mapper.class,
        nameGenerator=org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator.class)
class BusinessMapperConfiguration {
    @Bean("businessSqlSessionFactory")
    SqlSessionFactory businessSqlSessionFactory(MybatisPlusInterceptor interceptor,
            MetaObjectHandler audit, SqlTelemetry telemetry,
            com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator identifiers) throws Exception {
        return PersistenceConfiguration.factory(new BusinessRoutingDataSource(),interceptor,audit,telemetry,identifiers);
    }
}
