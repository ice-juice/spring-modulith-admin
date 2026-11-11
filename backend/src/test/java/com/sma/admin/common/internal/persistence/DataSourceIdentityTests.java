package com.sma.admin.common.internal.persistence;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.sql.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import com.sma.admin.common.persistence.DataSourceConnectionIdentity;
import com.sma.admin.common.persistence.DataSourceIdentity;

class DataSourceIdentityTests {
    @Test void allStatementTypesKeepTheBorrowedConnectionIdentityAndJdbcContracts() throws Exception {
        var pool = mock(DataSource.class);
        var physical = mock(Connection.class);
        var plain = mock(Statement.class);
        var prepared = mock(PreparedStatement.class);
        var callable = mock(CallableStatement.class);
        when(pool.getConnection()).thenReturn(physical);
        when(physical.createStatement()).thenReturn(plain);
        when(physical.prepareStatement("select ?")).thenReturn(prepared);
        when(physical.prepareCall("call p(?)")).thenReturn(callable);
        var identity = new DataSourceIdentity("tenant-business", 7);
        var source = new IdentifiedDataSource(pool, identity);
        try (var connection = source.getConnection()) {
            assertThat(connection.unwrap(DataSourceConnectionIdentity.class).dataSourceIdentity()).isEqualTo(identity);
            assertThat(connection.isWrapperFor(Connection.class)).isTrue();
            assertThat(connection.unwrap(Connection.class)).isSameAs(connection);
            assertThat(connection).isEqualTo(connection).isNotEqualTo(physical);
            try (var a = connection.createStatement(); var b = connection.prepareStatement("select ?");
                    var c = connection.prepareCall("call p(?)")) {
                for (var statement : new Statement[]{a,b,c}) assertThat(statement.getConnection()).isSameAs(connection);
                b.setLong(1, 99); verify(prepared).setLong(1, 99);
                c.registerOutParameter(1, Types.VARCHAR); verify(callable).registerOutParameter(1, Types.VARCHAR);
                assertThat(b.unwrap(PreparedStatement.class)).isSameAs(b);
            }
            verify(plain).close(); verify(prepared).close(); verify(callable).close();
        }
        verify(physical).close(); verify(physical, never()).getMetaData();
    }

    @Test void sqlExceptionsAreNotWrappedAndDiagnosticsDoNotExposeDriverStrings() throws Exception {
        var pool = mock(DataSource.class);
        var physical = mock(Connection.class);
        when(pool.getConnection("user", "secret")).thenReturn(physical);
        var failure = new SQLException("original", "42000", 7);
        when(physical.prepareStatement("bad")).thenThrow(failure);
        var source = new IdentifiedDataSource(pool, new DataSourceIdentity("warehouse", 2));
        var connection = source.getConnection("user", "secret");
        assertThatThrownBy(() -> connection.prepareStatement("bad")).isSameAs(failure);
        assertThat(source.toString()).isEqualTo("IdentifiedDataSource[warehouse:2]");
        assertThat(connection.toString()).isEqualTo("IdentifiedConnection[warehouse:2]");
    }

    @Test void existingConnectionsKeepTheirResourceRevisionAfterReplacement() throws Exception {
        var pool = mock(DataSource.class);
        when(pool.getConnection()).thenAnswer(ignored -> mock(Connection.class));
        var old = new IdentifiedDataSource(pool, new DataSourceIdentity("orders", 1)).getConnection();
        var next = new IdentifiedDataSource(pool, new DataSourceIdentity("orders", 2)).getConnection();
        assertThat(old.unwrap(DataSourceConnectionIdentity.class).dataSourceIdentity().revision()).isEqualTo(1);
        assertThat(next.unwrap(DataSourceConnectionIdentity.class).dataSourceIdentity().revision()).isEqualTo(2);
    }

    @Test void identityRejectsConnectionStringsAndInvalidRevisions() {
        assertThatThrownBy(() -> new DataSourceIdentity("jdbc:mysql://host/db", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DataSourceIdentity("orders", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DataSourceIdentity("unknown", 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
