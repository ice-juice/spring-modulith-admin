package com.sma.admin.rule.internal.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.sma.admin.common.security.PermissionCatalog;
import com.sma.admin.common.security.PermissionSpec;

/** Menu entries reuse existing view permissions; action grants keep their existing keys. */
@Component
public class RulePermissionCatalog implements PermissionCatalog {
    private static final Map<String, String> MENU_PATHS = Map.of("scene", "scenes", "template", "templates",
            "catalog", "catalog", "instance", "instances", "execution", "operations");
    @Override public List<PermissionSpec> permissions() {
        var result = new ArrayList<PermissionSpec>();
        result.add(new PermissionSpec("rule:center", null, "rule.menu.center", "规则中心", "DIRECTORY", null,
                "Connection", 80, "admin", true));
        for (String resource : List.of("scene", "template", "catalog", "instance", "simulation", "release", "execution", "test", "batch", "maintenance")) {
            List<String> actions = switch (resource) {
                case "scene" -> List.of("view", "edit");
                case "template" -> List.of("view", "edit", "publish", "lifecycle");
                case "catalog" -> List.of("view");
                case "instance" -> List.of("view", "edit", "publish", "disable");
                case "simulation" -> List.of("run");
                case "release" -> List.of("rollback");
                case "test" -> List.of("view", "edit", "run");
                case "batch" -> List.of("view", "migrate");
                case "maintenance" -> List.of("view", "clean");
                default -> List.of("view", "run");
            };
            for (String action : actions) {
                String path = action.equals("view") ? MENU_PATHS.get(resource) : null;
                result.add(new PermissionSpec("rule:" + resource + ":" + action, "rule:center",
                        "rule.permission." + resource + "." + action, "规则:" + resource + ":" + action,
                        path == null ? "BUTTON" : "MENU", path == null ? null : "/rule/" + path,
                        path == null ? null : "Connection", path == null ? 0 : 80, "admin", true));
            }
        }
        return result;
    }
}
