package com.sma.admin.common.internal.cache;

import static org.assertj.core.api.Assertions.*;
import java.net.URLClassLoader;
import java.nio.file.*;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sma.admin.common.cache.*;

class CacheResourceCompatibilityTests {
    @TempDir Path directory;

    @Test void previouslyCompiledInspectionAdapterAndSixArgumentTargetLinkAgainstTheNewHost() throws Exception {
        var source=directory.resolve("source");var output=directory.resolve("classes");
        Files.createDirectories(source.resolve("com/sma/admin/common/cache"));Files.createDirectories(output);
        var api=source.resolve("com/sma/admin/common/cache/CacheResourceAdapter.java");
        Files.writeString(api,"""
            package com.sma.admin.common.cache;
            public interface CacheResourceAdapter {
                record Snapshot(long entries,long entryLimit,long bytes,long protectedEntries,String lifecycleKey) {}
                String region(); Snapshot inspect();
            }
            """);
        var tasks=source.resolve("com/sma/admin/common/cache/CacheTasks.java");
        Files.writeString(tasks,"""
            package com.sma.admin.common.cache;
            public interface CacheTasks {
                record Target(String region,String nameKey,String scope,String managePermission,long controlVersion,String registrationHash) {}
            }
            """);
        var client=source.resolve("OldCacheClient.java");
        Files.writeString(client,"""
            import com.sma.admin.common.cache.*;
            public class OldCacheClient implements CacheResourceAdapter {
                public String region(){return "shop.runtime";}
                public Snapshot inspect(){return new Snapshot(2,10,-1,0,"shop.cache.runtime");}
                public CacheTasks.Target target(){return new CacheTasks.Target("shop.items","shop.cache.items","TENANT","shop:cache:manage",0,"old");}
            }
            """);
        assertThat(ToolProvider.getSystemJavaCompiler().run(null,null,null,"-proc:none","-d",output.toString(),api.toString(),tasks.toString(),client.toString())).isZero();
        // Parent-first loading binds the independently compiled client to the current host contracts.
        try(var loader=new URLClassLoader(new java.net.URL[]{output.toUri().toURL()},getClass().getClassLoader())){
            var instance=loader.loadClass("OldCacheClient").getConstructor().newInstance();
            var adapter=(CacheResourceAdapter)instance;
            assertThat(adapter.inspect().entries()).isEqualTo(2);assertThat(adapter.operationVersion()).isEqualTo(1);
            assertThatThrownBy(()->adapter.authorize(CacheDescriptor.Operation.CLEAR)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            assertThatThrownBy(()->adapter.execute(CacheDescriptor.Operation.CLEAR)).isInstanceOf(UnsupportedOperationException.class);
            var target=(CacheTasks.Target)instance.getClass().getMethod("target").invoke(instance);
            assertThat(target.resource()).isNull();assertThat(target.warmup()).isNull();assertThat(target.region()).isEqualTo("shop.items");
        }
    }
}
