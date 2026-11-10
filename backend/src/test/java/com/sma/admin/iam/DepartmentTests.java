package com.sma.admin.iam;

import static org.assertj.core.api.Assertions.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.internal.service.DepartmentService;
import com.sma.admin.support.*;

@IntegrationTest
@AutoConfigureMockMvc
class DepartmentTests {
    @Autowired DepartmentService service;
    @Autowired MockMvcTester mvc;
    @Test
    void hierarchyMovesDescendantsRejectsCyclesAndProtectsParents() {
        var user = Users.principal(101, 101, DataAccess.unrestricted());
        Users.as(user, () -> {
            var root = service.save(null, input(0L, "HQ", "hq"));
            var branch = service.save(null, input(root.id(), "Branch", "branch"));
            var leaf = service.save(null, input(branch.id(), "Leaf", "leaf"));
            var target = service.save(null, input(0L, "Target", "target"));
            assertThatThrownBy(() -> service.save(root.id(), input(leaf.id(), "HQ", "hq"))).isInstanceOf(BizException.class);
            assertThatThrownBy(() -> service.delete(branch.id())).isInstanceOf(BizException.class);
            service.save(branch.id(), input(target.id(), "Branch", "branch"));
            assertThat(service.require(leaf.id()).getPath()).isEqualTo("/" + target.id() + "/" + branch.id() + "/" + leaf.id() + "/");
            assertThat(service.tree(false)).hasSize(2);
            assertThatThrownBy(() -> service.save(null, input(0L, "Duplicate", "hq"))).isInstanceOf(BizException.class);
            service.delete(leaf.id()); service.delete(branch.id()); service.delete(root.id()); service.delete(target.id());
            return null;
        });
    }
    @Test
    void partialDataScopeCannotMoveOrDeleteAnIncompleteSubtree() {
        var full = Users.principal(101, 104, DataAccess.unrestricted());
        var root = Users.as(full, () -> service.save(null, input(0L, "Root", "root")));
        Users.as(full, () -> service.save(null, input(root.id(), "Child", "child")));
        Users.as(Users.principal(102, 104, new DataAccess(false, Set.of(root.id()), false)), () -> {
            assertThatThrownBy(() -> service.delete(root.id())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            assertThatThrownBy(() -> service.save(root.id(), input(0L, "Renamed", "root"))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            return null;
        });
    }
    @Test
    void tenantAndPermissionChecksApplyToEndpoints() {
        Users.as(Users.principal(101, 102, DataAccess.unrestricted()), () -> {
            assertThat(mvc.get().uri("/api/iam/departments").header("Accept-Language", "en-US"))
                    .hasStatus(403).bodyJson().hasPathSatisfying("$.message", v -> v.assertThat().isEqualTo("Access denied"));
            return null;
        });
        var admin = new SmaPrincipal(101, "admin", 102, "admin", null, true, DataAccess.unrestricted(), Set.of("iam:department:view", "iam:department:create"), "UTC");
        Users.as(admin, () -> {
            assertThat(mvc.post().uri("/api/iam/departments").contentType(MediaType.APPLICATION_JSON).content("""
                    {"parentId":"0","name":"Sales","code":"sales","sort":0,"enabled":true}
                    """)).hasStatusOk().bodyJson().hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo(0));
            return null;
        });
        Users.as(Users.principal(101, 103, DataAccess.unrestricted()), () -> {
            assertThat(service.list(null)).isEmpty(); return null;
        });
    }
    private DepartmentService.Input input(Long parent, String name, String code) { return new DepartmentService.Input(parent, name, code, 0, true); }
}
