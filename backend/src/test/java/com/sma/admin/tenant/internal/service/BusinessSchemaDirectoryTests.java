package com.sma.admin.tenant.internal.service;

import static org.assertj.core.api.Assertions.*;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import javax.tools.ToolProvider;
import com.sma.admin.common.persistence.BusinessSchemaCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.aop.framework.ProxyFactory;

class BusinessSchemaDirectoryTests {
    @TempDir Path directory;
    @Test void compiledBusinessOwnsDerivedLocationAndCannotImpersonateAnotherGroup()throws Exception {
        var source=directory.resolve("src/com/sma/business/shop/internal");Files.createDirectories(source);
        var info=source.getParent().resolve("package-info.java");
        Files.writeString(info,"@org.springframework.modulith.ApplicationModule(allowedDependencies={\"common :: persistence\"}) package com.sma.business.shop;");
        var catalog=source.resolve("SchemaCatalog.java");
        Files.writeString(catalog,"""
            package com.sma.business.shop.internal;
            public class SchemaCatalog implements com.sma.admin.common.persistence.BusinessSchemaCatalog {
                private final String group; public SchemaCatalog(String group){this.group=group;}
                public String storageGroup(){return group;}
            }
            """);
        assertThat(ToolProvider.getSystemJavaCompiler().run(null,null,null,"-proc:none","-classpath",System.getProperty("java.class.path"),"-d",directory.toString(),info.toString(),catalog.toString())).isZero();
        try(var loader=new URLClassLoader(new java.net.URL[]{directory.toUri().toURL()},getClass().getClassLoader())) {
            var constructor=loader.loadClass("com.sma.business.shop.internal.SchemaCatalog").getConstructor(String.class);
            var own=(BusinessSchemaCatalog)constructor.newInstance("shop");
            var proxy=(BusinessSchemaCatalog)new ProxyFactory(own).getProxy();
            var registry=new BusinessSchemaDirectory(List.of(proxy));
            assertThat(registry.require("shop")).singleElement().satisfies(schema->{
                assertThat(schema.location()).isEqualTo("classpath:db/business/shop");
                assertThat(schema.history()).isEqualTo("flyway_schema_history_business_shop");
            });
            assertThatThrownBy(()->new BusinessSchemaDirectory(List.of(own,proxy))).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(()->new BusinessSchemaDirectory(List.of((BusinessSchemaCatalog)constructor.newInstance("other")))).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(()->registry.require("other")).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void platformOrUndeclaredCatalogCannotSupplyExecutableMigrationPaths(){
        assertThatThrownBy(()->new BusinessSchemaDirectory(List.of(()->"shop"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new BusinessSchemaDirectory(List.of()).require("shop")).isInstanceOf(IllegalStateException.class);
    }
}
