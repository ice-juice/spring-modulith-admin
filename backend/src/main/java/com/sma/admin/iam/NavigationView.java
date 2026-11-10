package com.sma.admin.iam;
import java.util.Set;
public record NavigationView(Long id,String name,String icon,Set<Long> menuIds) {
    public NavigationView { menuIds=Set.copyOf(menuIds); }
}
