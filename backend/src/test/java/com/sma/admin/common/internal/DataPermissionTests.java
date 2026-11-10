package com.sma.admin.common.internal;

import static org.assertj.core.api.Assertions.*;
import java.util.Set;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sma.admin.common.PageQuery;
import com.sma.admin.common.internal.persistence.*;
import com.sma.admin.common.security.*;
import com.sma.admin.support.*;

@IntegrationTest
class DataPermissionTests {
    @Autowired ProbeMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS common_test_record (
                  id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, name VARCHAR(64), dept_id BIGINT,
                  created_at DATETIME(3), created_by BIGINT, updated_at DATETIME(3), updated_by BIGINT,
                  deleted TINYINT NOT NULL DEFAULT 0, INDEX ix_tenant (tenant_id, deleted))
                """);
        jdbc.update("DELETE FROM common_test_record");
        jdbc.update("""
                INSERT INTO common_test_record (id,tenant_id,name,dept_id,created_by)
                VALUES (1,1,'own',10,101),(2,1,'child-dept',11,202),(3,1,'outside',99,303),(4,2,'other-tenant',10,101)
                """);
    }

    @Test
    void selfDepartmentUnionNoneAndAllScopesAlsoConstrainPaginationCounts() {
        check(Users.principal(101, 1, DataAccess.own()), List.of(1L));
        check(Users.principal(101, 1, new DataAccess(false, Set.of(10L, 11L), false)), List.of(1L, 2L));
        check(Users.principal(101, 1, new DataAccess(false, Set.of(11L), true)), List.of(1L, 2L));
        check(Users.principal(101, 1, DataAccess.none()), List.of());
        check(Users.principal(101, 1, DataAccess.unrestricted()), List.of(1L, 2L, 3L));
        check(new SmaPrincipal(101, "super", 1, "admin", 10L, true, DataAccess.none(), Set.of(), "UTC"), List.of(1L, 2L, 3L));
    }

    @Test
    void aliasedQueriesGroupScopeConditionsWithExistingFilters() {
        Users.as(Users.principal(101, 1, new DataAccess(false, Set.of(11L), true)), () -> {
            assertThat(mapper.namesExcept("own")).containsExactly("child-dept");
            return null;
        });
    }

    @Test
    void updatesAndLogicalDeletesRespectDataScope() {
        Users.as(Users.principal(101, 1, DataAccess.own()), () -> {
            ProbeEntity outside = new ProbeEntity(); outside.setId(2L); outside.setName("attacked");
            assertThat(mapper.updateById(outside)).isZero();
            assertThat(mapper.deleteById(2L)).isZero();
            ProbeEntity own = new ProbeEntity(); own.setId(1L); own.setName("changed");
            assertThat(mapper.updateById(own)).isEqualTo(1);
            assertThat(mapper.deleteById(1L)).isEqualTo(1);
            return null;
        });
        assertThat(jdbc.queryForObject("SELECT name FROM common_test_record WHERE id=2", String.class)).isEqualTo("child-dept");
        assertThat(jdbc.queryForObject("SELECT deleted FROM common_test_record WHERE id=1", Integer.class)).isEqualTo(1);
    }

    private void check(SmaPrincipal user, List<Long> ids) {
        Users.as(user, () -> {
            assertThat(mapper.selectList(new QueryWrapper<ProbeEntity>().orderByAsc("id"))).extracting(ProbeEntity::getId).containsExactlyElementsOf(ids);
            assertThat(mapper.selectPage(new PageQuery(1, 1, null, null).toPage(Map.of()), null).getTotal()).isEqualTo(ids.size());
            return null;
        });
    }
}
