package com.sma.admin.common.internal;

import static org.assertj.core.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sma.admin.common.*;
import com.sma.admin.common.internal.persistence.*;
import com.sma.admin.common.security.DataAccess;
import com.sma.admin.support.*;

@IntegrationTest
class PersistenceTests {
    @Autowired ProbeMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void tenantIsForcedOnInsertAndAppliedToReadsUpdatesAndDeletes() {
        jdbc.execute("DROP TABLE IF EXISTS common_test_record");
        jdbc.execute("""
                CREATE TABLE common_test_record (
                  id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, name VARCHAR(64), dept_id BIGINT,
                  created_at DATETIME(3), created_by BIGINT, updated_at DATETIME(3), updated_by BIGINT,
                  deleted TINYINT NOT NULL DEFAULT 0, INDEX ix_tenant (tenant_id, deleted))
                """);
        var first = Users.principal(101, 1, DataAccess.unrestricted());
        var other = Users.principal(202, 2, DataAccess.unrestricted());
        ProbeEntity entity = new ProbeEntity();
        entity.setName("tenant-one"); entity.setTenantId(999L);
        Users.as(first, () -> mapper.insert(entity));
        assertThat(entity.getTenantId()).isEqualTo(1L);
        assertThat(entity.getCreatedBy()).isEqualTo(101L);
        assertThat(entity.getCreatedAt()).isNotNull();
        Users.as(other, () -> {
            assertThat(mapper.selectById(entity.getId())).isNull();
            ProbeEntity change = new ProbeEntity(); change.setId(entity.getId()); change.setName("attacked");
            assertThat(mapper.updateById(change)).isZero();
            assertThat(mapper.deleteById(entity.getId())).isZero();
            assertThat(mapper.selectPage(new PageQuery(1, 20, null, null).toPage(Map.of()), null).getTotal()).isZero();
            return null;
        });
        Users.as(first, () -> {
            assertThatThrownBy(() -> mapper.updateAllUnsafely()).hasStackTraceContaining("Prohibition of table update operation");
            ProbeEntity change = new ProbeEntity(); change.setId(entity.getId()); change.setTenantId(2L);
            change.setName("updated");
            assertThat(mapper.updateById(change)).isEqualTo(1);
            assertThat(mapper.selectById(entity.getId()).getTenantId()).isEqualTo(1L);
            assertThat(mapper.selectPage(new PageQuery(1, 1, "name", "asc").toPage(Map.of("name", "name")), null).getTotal()).isEqualTo(1);
            assertThat(mapper.deleteById(entity.getId())).isEqualTo(1);
            assertThat(mapper.selectList(new QueryWrapper<>())).isEmpty();
            return null;
        });
        assertThat(jdbc.queryForObject("SELECT deleted FROM common_test_record WHERE id=?", Integer.class, entity.getId())).isEqualTo(1);
    }

    @Test
    void paginationRejectsUnboundedSizesAndUntrustedSqlSorts() {
        assertThatThrownBy(() -> new PageQuery(1, 201, null, null).toPage(Map.of())).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> new PageQuery(1, 20, "name; DROP TABLE x", "asc").toPage(Map.of())).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> new PageQuery(1, 20, "name", "garbage").toPage(Map.of("name", "name"))).isInstanceOf(BizException.class);
    }

    @Test
    void treesRejectDisconnectedCycles() {
        record Row(Long id, Long parent) {}
        assertThatThrownBy(() -> Trees.build(java.util.List.of(new Row(1L, 2L), new Row(2L, 1L)),
                Row::id, Row::parent, row -> "x", row -> false)).isInstanceOf(IllegalArgumentException.class);
    }
}
