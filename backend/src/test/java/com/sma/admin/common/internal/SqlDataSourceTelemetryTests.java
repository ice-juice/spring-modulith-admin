package com.sma.admin.common.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.sql.*;
import javax.sql.DataSource;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.RoutingStatementHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.Test;
import com.sma.admin.common.internal.persistence.IdentifiedDataSource;
import com.sma.admin.common.persistence.DataSourceIdentity;
import com.sma.admin.common.security.TenantScope;

class SqlDataSourceTelemetryTests {
    private static final String ID = "com.sma.business.shop.internal.persistence.OrderMapper.update";

    @Test void sameMapperIsSeparatedByActualResourceRevisionAndTenantIncludingFailures() throws Exception {
        var telemetry = new SqlTelemetry(0);
        var a = statement("orders-a", 1);
        var b = statement("orders-b", 1);
        var revised = statement("orders-a", 2);
        execute(telemetry, a, 41); execute(telemetry, b, 41); execute(telemetry, revised, 41);
        var failure = new SQLException("do not retain this credential", "23000");
        when(a.execute()).thenThrow(failure);
        assertThatThrownBy(() -> execute(telemetry, a, 41)).hasRootCause(failure);
        var rows = TenantScope.within(41, telemetry::statements);
        assertThat(rows).hasSize(3).allSatisfy(row -> assertThat(row.id()).isEqualTo(ID));
        var original = rows.stream().filter(row -> row.resourceId().equals("orders-a") && row.resourceRevision() == 1).findFirst().orElseThrow();
        assertThat(original.calls()).isEqualTo(2);
        assertThat(original.errors()).isEqualTo(1);
        assertThat(original.rows()).isEqualTo(1);
        assertThat(original.slowCalls()).isEqualTo(2);
        assertThat(rows.toString()).doesNotContain("credential", "jdbc:", "password");
        assertThat(TenantScope.within(42, telemetry::statements)).isEmpty();
        execute(telemetry, b, 42);
        TenantScope.within(42, () -> { telemetry.reset(); return null; });
        assertThat(TenantScope.within(42, telemetry::statements)).isEmpty();
        assertThat(TenantScope.within(41, telemetry::statements)).hasSize(3);
    }

    @Test void missingOrBrokenIdentityNeverInventsPlatformAttributionOrPreventsSql() throws Exception {
        var telemetry = new SqlTelemetry(500);
        var untagged = mock(PreparedStatement.class);
        when(untagged.getConnection()).thenThrow(new SQLException("closed metadata"));
        when(untagged.getUpdateCount()).thenReturn(3);
        execute(telemetry, untagged, 41);
        verify(untagged).execute();
        assertThat(TenantScope.within(41, telemetry::statements)).singleElement().satisfies(row -> {
            assertThat(row.resourceId()).isEqualTo("unknown");
            assertThat(row.resourceRevision()).isZero();
            assertThat(row.errors()).isZero();
            assertThat(row.rows()).isEqualTo(3);
        });
    }

    private static PreparedStatement statement(String resource, long revision) throws Exception {
        var source = mock(DataSource.class);
        var physical = mock(Connection.class);
        var raw = mock(PreparedStatement.class);
        when(source.getConnection()).thenReturn(physical);
        when(physical.prepareStatement("update")).thenReturn(raw);
        when(raw.getUpdateCount()).thenReturn(1);
        // A spy preserves JDBC identity while permitting a driver execution failure on this concrete handle.
        return spy(new IdentifiedDataSource(source, new DataSourceIdentity(resource, revision)).getConnection().prepareStatement("update"));
    }

    private static void execute(SqlTelemetry telemetry, PreparedStatement jdbc, long tenant) {
        var config = new Configuration();
        var mapped = new MappedStatement.Builder(config, ID, new StaticSqlSource(config, "update test set value = ?"), SqlCommandType.UPDATE).build();
        var handler = new RoutingStatementHandler(mock(Executor.class), mapped, null, RowBounds.DEFAULT, null, null);
        TenantScope.within(tenant, () -> {
            try { return telemetry.intercept(new Invocation(handler, StatementHandler.class.getMethod("update", java.sql.Statement.class), new Object[]{jdbc})); }
            catch (Throwable failure) { throw new RuntimeException(failure); }
        });
    }
}
