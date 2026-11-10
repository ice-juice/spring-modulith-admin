package com.sma.admin.iam;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.internal.service.PostService;
import com.sma.admin.support.*;

@IntegrationTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class PostTests {
    @Autowired PostService service;
    @Autowired org.springframework.test.web.servlet.assertj.MockMvcTester mvc;
    @Test
    void pageEndpointBindsValidatedPagingAndRequiresPermission() {
        var viewer = new SmaPrincipal(101,"viewer",113,"admin",null,false,DataAccess.unrestricted(),java.util.Set.of("iam:post:view"),"UTC");
        Users.as(viewer, () -> {
            assertThat(mvc.get().uri("/api/iam/posts").param("page","1").param("size","10")).hasStatusOk()
                    .bodyJson().hasPathSatisfying("$.data.total", v -> v.assertThat().isEqualTo("0"));
            assertThat(mvc.get().uri("/api/iam/posts").param("size","1000")).hasStatusOk()
                    .bodyJson().hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo(400));
            return null;
        });
        Users.as(Users.principal(101,113,DataAccess.unrestricted()), () -> { assertThat(mvc.get().uri("/api/iam/posts")).hasStatus(403); return null; });
    }
    @Test
    void pagingOptionsCodeUniquenessAndLogicalDeleteReuseAreTenantSafe() {
        Users.as(Users.principal(101, 111, DataAccess.unrestricted()), () -> {
            var a = service.save(null, new PostService.Input("Engineer", "eng", 2, true, null));
            var b = service.save(null, new PostService.Input("Manager", "mgr", 1, false, null));
            assertThat(service.options()).extracting(OptionItem::value).containsExactly(a.id());
            assertThat(service.page(new PageQuery(1, 1, null, null), null, null).total()).isEqualTo(2);
            assertThat(service.page(new PageQuery(1, 20, null, null), "eng", true).items()).extracting(PostService.View::id).containsExactly(a.id());
            assertThatThrownBy(() -> service.save(null, new PostService.Input("Dup", "eng", 0, true, null))).isInstanceOf(BizException.class);
            Users.as(Users.principal(202, 112, DataAccess.unrestricted()), () -> {
                assertThat(service.options()).isEmpty();
                assertThatThrownBy(() -> service.delete(a.id())).isInstanceOf(BizException.class);
                service.save(null, new PostService.Input("Engineer", "eng", 0, true, null));
                return null;
            });
            service.delete(a.id());
            var c = service.save(null, new PostService.Input("Recreated", "eng", 0, true, null)); service.delete(c.id());
            var d = service.save(null, new PostService.Input("Recreated again", "eng", 0, true, null)); service.delete(d.id());
            service.delete(b.id());
            return null;
        });
    }
}
