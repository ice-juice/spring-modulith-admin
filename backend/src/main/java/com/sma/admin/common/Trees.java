package com.sma.admin.common;

import java.util.*;
import java.util.function.Function;

public final class Trees {
    private Trees() {}
    public static <T> List<TreeNode> build(List<T> rows, Function<T,Long> id, Function<T,Long> parent,
            Function<T,String> label, Function<T,Boolean> disabled) {
        Map<Long,T> indexed = new LinkedHashMap<>();
        rows.forEach(row -> { if (indexed.put(id.apply(row), row) != null) { throw new IllegalArgumentException("Duplicate tree id"); } });
        // Validate all chains, including cycles disconnected from roots.
        for (T row : rows) {
            Set<Long> visited = new HashSet<>();
            T cursor = row;
            while (cursor != null) {
                if (!visited.add(id.apply(cursor))) { throw new IllegalArgumentException("Cyclic tree"); }
                cursor = indexed.get(parent.apply(cursor));
            }
        }
        Map<Long,List<T>> children = new HashMap<>();
        rows.forEach(row -> children.computeIfAbsent(parent.apply(row), key -> new ArrayList<>()).add(row));
        return rows.stream().filter(row -> !indexed.containsKey(parent.apply(row)))
                .map(row -> node(row, children, id, parent, label, disabled)).toList();
    }
    private static <T> TreeNode node(T row, Map<Long,List<T>> children, Function<T,Long> id, Function<T,Long> parent,
            Function<T,String> label, Function<T,Boolean> disabled) {
        return new TreeNode(id.apply(row), parent.apply(row), label.apply(row), disabled.apply(row),
                children.getOrDefault(id.apply(row), List.of()).stream()
                        .map(child -> node(child, children, id, parent, label, disabled)).toList());
    }
}
