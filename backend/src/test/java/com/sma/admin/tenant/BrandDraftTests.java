package com.sma.admin.tenant;

import static org.assertj.core.api.Assertions.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.support.*;
import com.sma.admin.tenant.internal.persistence.*;
import com.sma.admin.tenant.internal.service.BrandDraftService;

@IntegrationTest
class BrandDraftTests {
    @Autowired BrandDraftService drafts;
    @Autowired BrandHeadMapper heads;
    @Autowired BrandRevisionMapper revisions;
    private static SmaPrincipal actor(long tenant,String portal,Set<String> permissions) {
        return new SmaPrincipal(701,"brand-editor",tenant,portal,null,false,DataAccess.unrestricted(),permissions,"UTC");
    }
    private static SmaPrincipal editor(long tenant) { return actor(tenant,"admin",Set.of("tenant:brand:view","tenant:brand:edit")); }

    @Test void savingDraftDoesNotPublishAndScopesAreTenantAndPortalBound() {
        Users.as(editor(7301),() -> {
            assertThat(drafts.get().version()).isZero();
            var saved=drafts.save(0,BrandContent.defaults("Tenant A brand"));
            assertThat(saved.version()).isEqualTo(1);assertThat(saved.publishedRevisionId()).isNull();
            assertThat(revisions.selectCount(null)).isZero();
            assertThatThrownBy(() -> drafts.save(0,BrandContent.defaults("Stale overwrite"))).isInstanceOf(BizException.class);
            assertThat(drafts.get().content().identity().systemName()).isEqualTo("Tenant A brand");
            Users.as(editor(7302),() -> { assertThat(drafts.get().version()).isZero();assertThat(drafts.get().content().identity().systemName()).doesNotContain("Tenant A brand");return null; });
            Users.as(actor(7301,"partner",Set.of("tenant:brand:view","tenant:brand:edit")),() -> {
                assertThat(drafts.get().version()).isZero();drafts.save(0,BrandContent.defaults("Partner brand"));return null;
            });
            assertThat(drafts.get().content().identity().systemName()).isEqualTo("Tenant A brand");return null;
        });
    }

    @Test void permissionsAndInvalidContentCannotCreateOrModifyDrafts() {
        Users.as(actor(7303,"admin",Set.of("tenant:brand:view")),() -> {
            assertThatThrownBy(() -> drafts.save(0,BrandContent.defaults("Forbidden"))).isInstanceOf(AccessDeniedException.class);
            assertThat(heads.selectCount(null)).isZero();return null;
        });
        Users.as(editor(7303),() -> {
            assertThatThrownBy(() -> drafts.save(0,BrandContent.defaults("<script>bad</script>"))).isInstanceOf(BizException.class);
            assertThat(heads.selectCount(null)).isZero();return null;
        });
        Users.as(SmaPrincipal.forApplication(7303,"admin","machine"),() -> {
            assertThatThrownBy(drafts::get).isInstanceOf(AccessDeniedException.class);return null;
        });
    }

    @Test void concurrentEditorsWithSameVersionHaveExactlyOneWinner() throws Exception {
        var editor=editor(7304);Users.as(editor,() -> drafts.save(0,BrandContent.defaults("Initial")));
        var start=new java.util.concurrent.CountDownLatch(1);
        var successes=new java.util.concurrent.atomic.AtomicInteger();var conflicts=new java.util.concurrent.atomic.AtomicInteger();
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var jobs=java.util.stream.Stream.of("First","Second").map(name -> pool.submit(() -> {
                start.await();return Users.as(editor,() -> {
                    try { drafts.save(1,BrandContent.defaults(name));successes.incrementAndGet(); }
                    catch(BizException conflict) { assertThat(conflict.getErrorCode()).isEqualTo(TenantErrorCode.STALE);conflicts.incrementAndGet(); }
                    return null;
                });
            })).toList();
            start.countDown();for(var job:jobs) job.get(20,java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(successes.get()).isEqualTo(1);assertThat(conflicts.get()).isEqualTo(1);
        Users.as(editor,() -> { assertThat(drafts.get().version()).isEqualTo(2);assertThat(drafts.get().publishedRevisionId()).isNull();return null; });
    }
}
