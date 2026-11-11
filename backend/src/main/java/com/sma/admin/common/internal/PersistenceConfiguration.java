package com.sma.admin.common.internal;

import static com.sma.admin.SpringbootModulithAdminApplication.BUSINESS_PACKAGE;
import static com.sma.admin.SpringbootModulithAdminApplication.PLATFORM_PACKAGE;

import java.time.Instant;
import org.apache.ibatis.reflection.MetaObject;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.sma.admin.common.persistence.TenantEntity;
import com.sma.admin.common.security.CurrentUser;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackages = {PLATFORM_PACKAGE, BUSINESS_PACKAGE}, annotationClass = org.apache.ibatis.annotations.Mapper.class,
        nameGenerator = org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator.class)
class PersistenceConfiguration {
    @Bean
    MybatisPlusInterceptor mybatisPlusInterceptor() {
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override public Expression getTenantId() { return new LongValue(CurrentUser.tenantId()); }
            @Override public boolean ignoreTable(String tableName) {
                // Only mapped tenantless tables are exempt. Unknown tables fail closed.
                var info = TableInfoHelper.getTableInfos().stream()
                        .filter(t -> t.getTableName().equalsIgnoreCase(tableName.replace("`", "")))
                        .findFirst().orElseThrow(() -> new IllegalStateException("Unregistered MyBatis table: " + tableName));
                return !TenantEntity.class.isAssignableFrom(info.getEntityType());
            }
        }));
        interceptor.addInnerInterceptor(new DataPermissionInterceptor(new RoleDataPermissionHandler()));
        var pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(200L);
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }

    @Bean
    MetaObjectHandler auditFields() {
        return new MetaObjectHandler() {
            @Override public void insertFill(MetaObject object) {
                Instant now = Instant.now();
                if (object.hasSetter("tenantId")) { setFieldValByName("tenantId", CurrentUser.tenantId(), object); }
                setFieldValByName("createdAt", now, object);
                setFieldValByName("createdBy", CurrentUser.userId(), object);
                fillUpdate(object, now);
                if (object.hasSetter("deleted")) { setFieldValByName("deleted", false, object); }
            }
            @Override public void updateFill(MetaObject object) { fillUpdate(object, Instant.now()); }
            private void fillUpdate(MetaObject object, Instant now) {
                setFieldValByName("updatedAt", now, object);
                setFieldValByName("updatedBy", CurrentUser.userId(), object);
            }
        };
    }
}
