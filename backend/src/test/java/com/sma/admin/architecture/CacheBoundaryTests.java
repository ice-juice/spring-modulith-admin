package com.sma.admin.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

class CacheBoundaryTests {
    @Test void onlyTheCommonCacheRuntimeMayUseStorageClientsOrFrameworkCaches(){
        boundary().check(ArchitectureRulesTests.CLASSES);
    }
    @Test void independentlyCompiledBusinessCannotBypassTheBoundary(@TempDir Path directory) throws Exception {
        var good=directory.resolve("GoodCache.java");var bad=directory.resolve("BadCache.java");
        Files.writeString(good,"package com.sma.business.shop.internal; public class GoodCache { public com.sma.admin.common.cache.ManagedCache cache; }");
        Files.writeString(bad,"package com.sma.business.shop.internal; public class BadCache { public org.springframework.data.redis.core.StringRedisTemplate cache; }");
        assertThat(ToolProvider.getSystemJavaCompiler().run(null,null,null,"-proc:none","-classpath",System.getProperty("java.class.path"),"-d",directory.toString(),good.toString(),bad.toString())).isZero();
        boundary().check(new ClassFileImporter().importPath(directory.resolve("com/sma/business/shop/internal/GoodCache.class")));
        assertThatThrownBy(()->boundary().check(new ClassFileImporter().importPath(directory.resolve("com/sma/business/shop/internal/BadCache.class"))))
                .isInstanceOf(AssertionError.class).hasMessageContaining("StringRedisTemplate");
    }
    private ArchRule boundary(){
        return noClasses().that().resideOutsideOfPackage("com.sma.admin.common.internal.cache..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.data.redis..","io.lettuce..","redis.clients..",
                        "org.redisson..","com.github.benmanes.caffeine..","org.springframework.cache..","jakarta.cache..","javax.cache..")
                .because("platform and static business caches use the common :: cache contract; reviewed runtime objects use owning adapters");
    }
}
