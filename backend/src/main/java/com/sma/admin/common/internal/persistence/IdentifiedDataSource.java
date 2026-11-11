package com.sma.admin.common.internal.persistence;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import com.sma.admin.common.persistence.DataSourceConnectionIdentity;
import com.sma.admin.common.persistence.DataSourceIdentity;

/** Decorates a concrete pool, never a routing facade. Identity lives as long as each borrowed connection. */
public final class IdentifiedDataSource extends DelegatingDataSource implements AutoCloseable {
    private final DataSourceIdentity identity;

    public IdentifiedDataSource(DataSource target, DataSourceIdentity identity) {
        super(Objects.requireNonNull(target));
        this.identity = Objects.requireNonNull(identity);
        if (DataSourceIdentity.UNKNOWN.equals(identity)) throw new IllegalArgumentException("A configured source needs an identity");
    }

    @Override public Connection getConnection() throws SQLException { return identify(super.getConnection()); }
    @Override public Connection getConnection(String username, String password) throws SQLException {
        return identify(super.getConnection(username, password));
    }

    private Connection identify(Connection delegate) {
        return (Connection) Proxy.newProxyInstance(IdentifiedDataSource.class.getClassLoader(),
                new Class<?>[]{Connection.class, DataSourceConnectionIdentity.class}, (proxy, method, args) -> {
                    if (method.getDeclaringClass() == DataSourceConnectionIdentity.class) return identity;
                    Object local = wrapperMethod(proxy, method, args);
                    if (local != NOT_HANDLED) return local;
                    if (method.getName().equals("toString")) return "IdentifiedConnection[" + identity.resourceId() + ":" + identity.revision() + "]";
                    Object result = invoke(delegate, method, args);
                    if (result instanceof Statement statement) return identify(statement, (Connection) proxy);
                    return result;
                });
    }

    private Statement identify(Statement delegate, Connection connection) {
        Class<?> type = delegate instanceof CallableStatement ? CallableStatement.class
                : delegate instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
        return (Statement) Proxy.newProxyInstance(IdentifiedDataSource.class.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> {
                    if (method.getName().equals("getConnection")) return connection;
                    Object local = wrapperMethod(proxy, method, args);
                    if (local != NOT_HANDLED) return local;
                    if (method.getName().equals("toString")) return "IdentifiedStatement[" + identity.resourceId() + ":" + identity.revision() + "]";
                    return invoke(delegate, method, args);
                });
    }

    private static final Object NOT_HANDLED = new Object();
    private static Object wrapperMethod(Object proxy, Method method, Object[] args) {
        if (method.getName().equals("equals") && method.getParameterCount() == 1) return proxy == args[0];
        if (method.getName().equals("hashCode") && method.getParameterCount() == 0) return System.identityHashCode(proxy);
        if (args != null && args.length == 1 && args[0] instanceof Class<?> type && type.isInstance(proxy)) {
            if (method.getName().equals("unwrap")) return proxy;
            if (method.getName().equals("isWrapperFor")) return true;
        }
        return NOT_HANDLED;
    }

    private static Object invoke(Object delegate, Method method, Object[] args) throws Throwable {
        try { return method.invoke(delegate, args); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }

    @Override public void close() throws Exception {
        if (getTargetDataSource() instanceof AutoCloseable closeable) closeable.close();
    }

    @Override public String toString() { return "IdentifiedDataSource[" + identity.resourceId() + ":" + identity.revision() + "]"; }
}
