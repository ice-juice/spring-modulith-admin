package com.sma.admin.common.internal.persistence;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Business MyBatis uses the physical connection already bound by the business ledger transaction. */
public final class BusinessRoutingDataSource extends AbstractDataSource {
    @Override public Connection getConnection() throws SQLException {
        var source=BusinessRouteScope.require();
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            for (var key:TransactionSynchronizationManager.getResourceMap().keySet()) {
                if (key instanceof DataSource && key!=source && key!=this)
                    throw new IllegalStateException("Business Mapper cannot join a different resource transaction");
            }
        }
        var connection=DataSourceUtils.getConnection(source);
        var closed=new AtomicBoolean();
        return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},
                (proxy,method,args)->{
                    if (method.getName().equals("close") && method.getParameterCount()==0) {
                        if (closed.compareAndSet(false,true)) DataSourceUtils.releaseConnection(connection,source);
                        return null;
                    }
                    if (method.getName().equals("isClosed") && method.getParameterCount()==0) return closed.get();
                    if (method.getName().equals("equals") && method.getParameterCount()==1) return proxy==args[0];
                    if (method.getName().equals("hashCode") && method.getParameterCount()==0) return System.identityHashCode(proxy);
                    if (closed.get()) throw new SQLException("Business routing connection is closed");
                    try { return method.invoke(connection,args); }
                    catch (InvocationTargetException failure) { throw failure.getCause(); }
                });
    }
    @Override public Connection getConnection(String username,String password) throws SQLException {
        throw new SQLException("Business routing does not accept per-call credentials");
    }
}
