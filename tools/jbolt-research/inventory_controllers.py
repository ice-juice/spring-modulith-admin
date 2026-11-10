# -*- coding: utf-8 -*-
"""Inventory JBolt routes, controller actions and permission annotations."""
import re, json
from pathlib import Path
from paths import JBOLT_HOME, JBOLT_RUN_HOME, RESEARCH_DOCS  # noqa: E402

ROOT = JBOLT_HOME / "src" / "main" / "java"
OUT = RESEARCH_DOCS

java = {p: p.read_text(encoding="utf-8", errors="replace") for p in ROOT.rglob("*.java")}
simple_to_path = {}
for p, src in java.items():
    simple_to_path.setdefault(p.stem, []).append(p)

# 1) routes: this.add("/admin/user", UserAdminController.class, "/user") and scan packages
routes = []
for p, src in java.items():
    if "extends Routes" not in src and "new Routes()" not in src:
        continue
    base_view = re.search(r'setBaseViewPath\("([^"]*)"\)', src)
    interceptors = re.findall(r'addInterceptor\(new\s+(\w+)', src)
    for m in re.finditer(r'(?:this|me)\.add\("([^"]+)",\s*(\w+)\.class(?:,\s*"([^"]*)")?\)', src):
        routes.append({"routesFile": p.stem, "path": m.group(1), "controller": m.group(2), "view": (base_view.group(1) if base_view else "") + (m.group(3) or ""), "interceptors": interceptors})

# 2) @Path annotated controllers picked up by scan()
for p, src in java.items():
    m = re.search(r'@Path\(\s*(?:value\s*=\s*)?"([^"]+)"(?:\s*,\s*viewPath\s*=\s*"([^"]*)")?', src)
    if m and "Controller" in p.stem:
        routes.append({"routesFile": "@Path-scan", "path": m.group(1), "controller": p.stem, "view": m.group(2) or "", "interceptors": []})

ANN = re.compile(r"@(\w+)(\([^)]*\))?")
def parse_controller(name):
    paths = simple_to_path.get(name)
    if not paths:
        return None
    src = java[paths[0]]
    cls = re.search(r"((?:\s*@\w+(?:\([^)]*\))?\s*)*)public\s+class\s+(\w+)\s+extends\s+(\w+)", src)
    class_ann = [a.group(0) for a in ANN.finditer(cls.group(1))] if cls else []
    parent = cls.group(3) if cls else ""
    actions = []
    for m in re.finditer(r"((?:[ \t]*@\w+(?:\([^)]*\))?[ \t]*\r?\n)*)[ \t]*public\s+void\s+(\w+)\s*\(([^)]*)\)", src):
        anns = [a.group(0) for a in ANN.finditer(m.group(1))]
        actions.append({"action": m.group(2), "params": m.group(3).strip(), "annotations": anns})
    return {"file": str(paths[0].relative_to(ROOT)).replace("\\", "/"), "parent": parent, "classAnnotations": class_ann, "actions": actions}

inventory = []
for r in sorted(routes, key=lambda x: (x["routesFile"], x["path"])):
    c = parse_controller(r["controller"])
    inventory.append({**r, **(c or {"file": "(jbolt_core.jar)", "parent": "", "classAnnotations": [], "actions": []})})

(OUT / "jbolt-endpoints.json").write_text(json.dumps(inventory, ensure_ascii=False, indent=1), encoding="utf-8")
lines = ["# JBolt 路由与控制器动作清单（自动生成）", "",
         "> 由 tools/jbolt-research/inventory_controllers.py 扫描源码生成。JFinal 约定：URL = 路由前缀 + '/' + 方法名，index 方法对应路由前缀本身。", "",
         f"共 {len(inventory)} 个路由前缀，{sum(len(i['actions']) for i in inventory)} 个动作方法。", "",
         "| Routes | URL 前缀 | 控制器 | 视图目录 | 类级注解 | 动作数 |", "|---|---|---|---|---|---|"]
for i in inventory:
    lines.append(f"| {i['routesFile']} | {i['path']} | {i['controller']} | {i['view']} | {' '.join(i['classAnnotations'])} | {len(i['actions'])} |")
for i in inventory:
    lines += ["", f"## {i['path']}  ({i['controller']})", "", f"源码：{i['file']}；父类：{i['parent']}；路由拦截器：{', '.join(i['interceptors']) or '-'}；类级注解：{' '.join(i['classAnnotations']) or '-'}", ""]
    if i["actions"]:
        lines += ["| 动作 | 注解 | 参数 |", "|---|---|---|"]
        for a in i["actions"]:
            lines.append(f"| {a['action']} | {' '.join(a['annotations'])} | {a['params']} |")
(OUT / "05-endpoint-inventory.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
print(len(inventory), sum(len(i['actions']) for i in inventory))
for i in inventory:
    print(f"{i['routesFile']:28} {i['path']:42} {i['controller']:40} {len(i['actions'])}")

