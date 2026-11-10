# -*- coding: utf-8 -*-
"""Build JBolt feature catalog (menus -> pages -> UI elements -> actions) and config catalog from source + reference DB."""
import json, re, subprocess
from pathlib import Path
from paths import JBOLT_HOME, JBOLT_RUN_HOME, RESEARCH_DOCS  # noqa: E402

SRC = JBOLT_HOME
JAVA = SRC / "src/main/java"
WEB = SRC / "src/main/webapp"
OUT = RESEARCH_DOCS
ENDPOINTS = json.loads((OUT / "jbolt-endpoints.json").read_text(encoding="utf-8"))
MYSQL = Path.home() / "AppData/Local/Programs/mysql-8.4.10-winx64/bin/mysql.exe"


def sql(q):
    r = subprocess.run([str(MYSQL), "-uroot", "-proot", "--host=127.0.0.1", "--default-character-set=utf8mb4", "-N", "-B", "jboltai", "-e", q],
                       capture_output=True, text=True, encoding="utf-8")
    return [line.split("\t") for line in r.stdout.strip().splitlines() if line]


def strip(html):
    html = re.sub(r"<script[^>]*>.*?</script>", " ", html, flags=re.S) if False else html
    t = re.sub(r"<[^>]+>", " ", html)
    t = re.sub(r"#\([^)]*\)|\$\{[^}]*\}|&nbsp;", " ", t)
    return re.sub(r"\s+", " ", t).strip()


def javadocs(controller_file):
    text = (JAVA / controller_file).read_text(encoding="utf-8", errors="replace")
    docs = {}
    for m in re.finditer(r"/\*\*(.*?)\*/\s*((?:@\w+(?:\([^)]*\))?\s*)*)public\s+void\s+(\w+)\s*\(", text, re.S):
        lines = [re.sub(r"^\s*\*\s?", "", l).strip() for l in m.group(1).splitlines()]
        lines = [l for l in lines if l and not l.startswith("@")]
        docs[m.group(3)] = " ".join(lines)[:80]
    return docs


def page_info(view):
    d = WEB / view.strip("/")
    if not d.is_dir():
        return {}
    idx = next((d / n for n in ("index.html", "index_ajax.html") if (d / n).exists()), None)
    info = {}
    if idx:
        h = idx.read_text(encoding="utf-8", errors="replace")
        head, _, body = h.partition("jbolt_page_content")
        t = re.search(r"<h1[^>]*>(.*?)</h1>", h, re.S)
        info["title"] = strip(t.group(1)) if t else ""
        info["search"] = list(dict.fromkeys(re.findall(r'placeholder="([^"#$]+)"', head) + [x.strip("=") for x in re.findall(r'data-text="([^"#$]+)"', head)]))
        info["toolbar"] = list(dict.fromkeys(x for x in (strip(b) for b in re.findall(r"<button[^>]*>(.*?)</button>", head, re.S)) if x and len(x) < 20))
        info["columns"] = [x for x in (strip(c) for c in re.findall(r"<th[^>]*>(.*?)</th>", body, re.S)) if x and len(x) < 30]
        info["rowActions"] = list(dict.fromkeys(re.findall(r'<a [^>]*data-title="([^"#$]+)"', body)))
    forms = [f for f in ("_form.html", "add.html") if (d / f).exists()]
    if forms:
        h = (d / forms[0]).read_text(encoding="utf-8", errors="replace")
        info["formFields"] = list(dict.fromkeys(x for x in (strip(l) for l in re.findall(r"<label[^>]*>(.*?)</label>", h, re.S)) if x and len(x) < 20))
    return info


# permission tree from the original project (includes AI branch)
perms = json.loads((SRC / "src/main/resources/permission_init.json").read_text(encoding="utf-8"))
route_by_url = {e["path"].strip("/"): e for e in ENDPOINTS}


def find_route(url):
    u = (url or "").strip("/")
    return route_by_url.get(u) if u else None


lines = ["# 07 JBolt 功能目录（自动生成）", "",
         "> 由 tools/jbolt-research/build_feature_catalog.py 从 permission_init.json、控制器源码（Javadoc）、页面模板（_view）生成。",
         "> 每个菜单列出：页面标题、查询条件、工具栏按钮、列表字段、行操作、表单字段、控制器动作（含 Javadoc 说明）。", ""]
catalog = []


def walk(items, path):
    for it in items:
        title, key, url = it.get("title", ""), it.get("permissionKey", ""), it.get("url") or ""
        crumb = path + [title]
        level = len(crumb)
        entry = {"path": " / ".join(crumb), "key": key, "url": url, "isMenu": it.get("isMenu"), "openType": it.get("openType")}
        lines.append(("#" * min(level + 1, 4)) + f" {' / '.join(crumb)}")
        lines.append("")
        lines.append(f"- 权限 key：§{key}§；类型：{'菜单' if it.get('isMenu') else '按钮/逻辑权限'}；打开方式：{ {1: '主区域', 2: 'iframe', 3: '弹窗'}.get(it.get('openType'), it.get('openType')) }；超管默认：{'是' if it.get('isSystemAdminDefault') else '否'}" + (f"；URL：§{url}§" if url else ""))
        route = find_route(url)
        if route:
            entry["controller"] = route["controller"]
            info = page_info(route["view"]) if route.get("view") else {}
            entry["page"] = info
            lines.append(f"- 控制器：§{route['controller']}§（{route['file']}），类注解 {' '.join(route['classAnnotations']) or '-'}")
            for label, k in (("页面标题", "title"), ("查询条件", "search"), ("工具栏", "toolbar"), ("列表字段", "columns"), ("行操作", "rowActions"), ("表单字段", "formFields")):
                v = info.get(k)
                if v:
                    lines.append(f"- {label}：{v if isinstance(v, str) else '、'.join(v)}")
            docs = javadocs(route["file"]) if route.get("file", "").endswith(".java") else {}
            acts = route.get("actions", [])
            if acts:
                lines.extend(["", "| 动作 | URL | 说明（Javadoc） | 注解 |", "|---|---|---|---|"])
                for a in acts:
                    u = route["path"].rstrip("/") + ("" if a["action"] == "index" else "/" + a["action"])
                    lines.append(f"| {a['action']} | {u} | {docs.get(a['action'], '').replace('|', '/')} | {' '.join(a['annotations']).replace('|', '/')} |")
                entry["actions"] = [{"action": a["action"], "doc": docs.get(a["action"], ""), "annotations": a["annotations"]} for a in acts]
        lines.append("")
        catalog.append(entry)
        walk(it.get("items", []), crumb)


walk(perms, [])

# routes not reachable from the menu tree
menu_urls = {(p.get("url") or "").strip("/") for p in catalog}
others = [e for e in ENDPOINTS if e["path"].strip("/") not in menu_urls]
lines += ["## 菜单之外的路由", "", "这些路由不直接挂在菜单上，作为子页面、公共接口、对外 API 或测试入口存在。", "",
          "| 路由前缀 | 控制器 | 动作数 | 动作 |", "|---|---|---|---|"]
for e in others:
    lines.append(f"| {e['path']} | {e['controller']} | {len(e['actions'])} | {'、'.join(a['action'] for a in e['actions'])[:300]} |")

text = "\n".join(lines).replace("§", chr(96))
(OUT / "07-feature-catalog.md").write_text(text + "\n", encoding="utf-8")
(OUT / "jbolt-feature-catalog.json").write_text(json.dumps(catalog, ensure_ascii=False, indent=1), encoding="utf-8")

# config catalog from the running reference DB
cfg = sql("select ifnull(t.name,'(未分组)'), g.config_key, g.name, g.value_type, g.config_value, g.built_in from jb_global_config g left join jb_global_config_type t on t.id=g.type_id order by t.sort_rank, g.config_key")
ctypes = sql("select name, type_key, built_in, sort_rank from jb_global_config_type order by sort_rank")
dts = sql("select id, name, type_key, mode_level, is_build_in from jb_dictionary_type order by id")
dics = sql("select type_key, name, sn, pid from jb_dictionary order by type_key, sort_rank")
out = ["# 08 JBolt 配置目录（自动生成）", "", "> 由 build_feature_catalog.py 从运行中的 JBolt 参考库（jboltai）导出：全局参数、参数分组、内置字典。", "",
       "## 全局参数分组", "", "| 分组 | type_key | 内置 |", "|---|---|---|"]
out += [f"| {n} | {k} | {'是' if b == '1' else '否'} |" for n, k, b, _ in ctypes]
out += ["", f"## 全局参数（{len(cfg)} 项，首次访问时自动创建）", "", "| 分组 | KEY | 名称 | 值类型 | 当前值 | 内置 |", "|---|---|---|---|---|---|"]
out += [f"| {g} | {k} | {n} | {t} | {v.replace('|', '/')[:60]} | {'是' if b == '1' else '否'} |" for g, k, n, t, v, b in cfg]
by_type = {}
for tk, n, sn, pid in dics:
    by_type.setdefault(tk, []).append(f"{n}({sn})")
out += ["", f"## 内置字典（{len(dts)} 类，{len(dics)} 项）", "", "| 字典类型 | type_key | 层级 | 字典项（名称(编码)） |", "|---|---|---|---|"]
out += [f"| {n} | {k} | {'多级' if m == '2' else '一级'} | {'、'.join(by_type.get(k, []))[:400]} |" for _, n, k, m, b in dts]
(OUT / "08-config-catalog.md").write_text("\n".join(out) + "\n", encoding="utf-8")
print("menus", len(catalog), "with routes", sum(1 for c in catalog if c.get("controller")), "other routes", len(others), "configs", len(cfg), "dict types", len(dts), "dict items", len(dics))

