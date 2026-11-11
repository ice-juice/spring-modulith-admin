package com.sma.admin.common.internal.cache;

import static org.assertj.core.api.Assertions.*;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.aop.framework.ProxyFactory;
import com.sma.admin.common.cache.*;

class CacheRegistrationOwnershipTests {
    @TempDir Path directory;
    private final CacheRegistrationOwnership ownership=new CacheRegistrationOwnership();
    @Test void independentlyCompiledBusinessCatalogAcceptsItsOwnRegionAndRejectsImpersonationEvenBehindAProxy() throws Exception {
        try(var fixture=fixture()){
            var catalog=fixture.catalog("com.sma.business.shop.internal.ShopCatalog");
            assertThat(new CacheRegistry(List.of(catalog),ownership).require("shop.items").owner()).isEqualTo("shop");
            var proxy=new ProxyFactory(catalog).getProxy();
            ownership.validate(proxy,descriptor("shop.items","shop:cache:manage"));
            assertThatThrownBy(()->ownership.validate(proxy,descriptor("config.items","config:cache:manage"))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("another module");
            assertThatThrownBy(()->ownership.validate(proxy,descriptor("shop.items","monitor:cache:view"))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("business management");
            assertThatThrownBy(()->new CacheRegistry(List.of(catalog,catalog),ownership)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate");
        }
    }
    @Test void foreignAndUndeclaredProvidersCannotSupplyCatalogsOrWarmup() throws Exception {
        try(var fixture=fixture()){
            var other=fixture.catalog("com.sma.business.other.internal.OtherCatalog");
            assertThatThrownBy(()->ownership.validateLoader(other,descriptor("shop.items","shop:cache:manage"))).isInstanceOf(IllegalArgumentException.class);
            var missing=fixture.catalog("com.sma.business.missing.internal.MissingCatalog");
            assertThatThrownBy(()->new CacheRegistry(List.of(missing),ownership)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("module not declared");
            assertThatThrownBy(()->ownership.validate(new Object(),descriptor("shop.items","shop:cache:manage"))).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void existingPlatformCatalogRetainsItsMonitorPermissionAndIsValidatedAfterProxyUnwrapping(){
        var catalog=new com.sma.admin.config.internal.service.ConfigCacheCatalog();
        var proxy=(CacheCatalog)new ProxyFactory(catalog).getProxy();
        var registry=new CacheRegistry(List.of(proxy),ownership);
        assertThat(registry.regions()).hasSize(3);
        ownership.validateLoader(catalog,registry.require("config.dictionary-options"));
    }
    private CacheDescriptor descriptor(String id,String permission){return new CacheDescriptor(id,id.substring(0,id.indexOf('.')),id.substring(0,id.indexOf('.'))+".cache.items","catalog","read",CacheDescriptor.Kind.DATA,CacheDescriptor.Scope.TENANT,1,CachePolicy.defaults(),Set.of(CacheDescriptor.Operation.CLEAR),Set.of("save"),permission);}
    private record Fixture(URLClassLoader loader) implements AutoCloseable {
        CacheCatalog catalog(String name) throws Exception{return (CacheCatalog)loader.loadClass(name).getConstructor().newInstance();}
        @Override public void close() throws Exception{loader.close();}
    }
    private Fixture fixture() throws Exception {
        var sources=new ArrayList<String>();
        for(var module:List.of("shop","other","missing")){
            var path=directory.resolve("src/com/sma/business/"+module+"/internal");Files.createDirectories(path);
            String name=Character.toUpperCase(module.charAt(0))+module.substring(1)+"Catalog";
            var source=path.resolve(name+".java");
            Files.writeString(source,"""
                package com.sma.business.%s.internal;
                import java.util.*;
                import com.sma.admin.common.cache.*;
                public class %s implements CacheCatalog {
                    public Collection<CacheDescriptor> regions(){return List.of(new CacheDescriptor("%s.items","%s","%s.cache.items","catalog","read",CacheDescriptor.Kind.DATA,CacheDescriptor.Scope.TENANT,1,CachePolicy.defaults(),Set.of(CacheDescriptor.Operation.CLEAR),Set.of("save"),"%s:cache:manage"));}
                }
                """.formatted(module,name,module,module,module,module));sources.add(source.toString());
            if(!module.equals("missing")){
                var info=path.getParent().resolve("package-info.java");
                Files.writeString(info,"@org.springframework.modulith.ApplicationModule(displayName=\"Business fixture\",allowedDependencies={\"common :: cache\"}) package com.sma.business."+module+";");sources.add(info.toString());
            }
        }
        var args=new ArrayList<>(List.of("-proc:none","-classpath",System.getProperty("java.class.path"),"-d",directory.toString()));args.addAll(sources);
        assertThat(ToolProvider.getSystemJavaCompiler().run(null,null,null,args.toArray(String[]::new))).isZero();
        return new Fixture(new URLClassLoader(new java.net.URL[]{directory.toUri().toURL()},getClass().getClassLoader()));
    }
}
