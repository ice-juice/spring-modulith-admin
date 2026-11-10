package com.sma.admin.iam;

import java.util.List;
import java.util.Set;
public interface PermissionQuery {
    Set<String> keys(Set<Long> roleIds, boolean superAdmin);
    List<MenuView> menus(Set<Long> roleIds, boolean superAdmin);
}
