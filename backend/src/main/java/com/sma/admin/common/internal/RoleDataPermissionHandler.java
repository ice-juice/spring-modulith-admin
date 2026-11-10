package com.sma.admin.common.internal;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import com.sma.admin.common.security.*;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;

/** Adds a grouped grant predicate alongside existing WHERE and tenant conditions, including paging counts. */
final class RoleDataPermissionHandler implements MultiDataPermissionHandler {
    private final Map<String, Optional<DataScoped>> descriptors = new ConcurrentHashMap<>();

    @Override
    public Expression getSqlSegment(Table table, Expression where, String statementId) {
        DataScoped scope = descriptors.computeIfAbsent(statementId, this::descriptor).orElse(null);
        if (scope == null || !table.getName().replace("`", "").equalsIgnoreCase(scope.table())) { return null; }
        var identity = CurrentUser.optional();
        if (identity.isEmpty()) { return null; } // bootstrap/system work; tenant remains default 1
        var user = identity.get();
        if (user.superAdmin() || user.dataAccess().all()) { return null; }
        String qualifier = table.getAlias() == null ? table.getName() : table.getAlias().getName();
        if (!qualifier.matches("[a-zA-Z_][a-zA-Z_0-9]*")) { throw new IllegalStateException("Unsafe SQL alias"); }
        List<String> grants = new ArrayList<>();
        if (!scope.department().isBlank() && !user.dataAccess().departmentIds().isEmpty()) {
            String ids = user.dataAccess().departmentIds().stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
            grants.add(qualifier + "." + scope.department() + " IN (" + ids + ")");
        }
        if (!scope.owner().isBlank() && user.dataAccess().self()) {
            grants.add(qualifier + "." + scope.owner() + " = " + user.userId());
        }
        try { return CCJSqlParserUtil.parseCondExpression(grants.isEmpty() ? "1 = 0" : "(" + String.join(" OR ", grants) + ")"); }
        catch (net.sf.jsqlparser.JSQLParserException ex) { throw new IllegalStateException("Invalid data permission predicate", ex); }
    }

    private Optional<DataScoped> descriptor(String statement) {
        try {
            Class<?> mapper = Class.forName(statement.substring(0, statement.lastIndexOf('.')));
            DataScoped descriptor = mapper.getAnnotation(DataScoped.class);
            if (descriptor != null && (!descriptor.table().matches("[a-z][a-z0-9_]*")
                    || !descriptor.department().matches("[a-z_]*") || !descriptor.owner().matches("[a-z_]*"))) {
                throw new IllegalStateException("DataScoped requires trusted SQL identifiers");
            }
            return Optional.ofNullable(descriptor);
        } catch (ClassNotFoundException ex) { throw new IllegalStateException("Unknown mapper for " + statement, ex); }
    }
}
