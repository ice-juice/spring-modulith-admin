# -*- coding: utf-8 -*-
"""Parse JBolt MySQL DDL files into a Markdown data dictionary and a JSON model."""
import json, re
from pathlib import Path
from paths import JBOLT_HOME, JBOLT_RUN_HOME, RESEARCH_DOCS  # noqa: E402

SRC = JBOLT_HOME / "readme"
OUT = RESEARCH_DOCS
FILES = [
    ("platform", SRC / "jbolt_mysql_id_long.sql"),
    ("ai", SRC / "jbolt_ai.sql"),
    ("custom_business", SRC / "custombusiness" / "custom_business_mysql.sql"),
]
BT = chr(96)
TABLE_RE = re.compile(r"CREATE TABLE\s+" + BT + r"(?P<name>[A-Za-z0-9_]+)" + BT + r"\s*\((?P<body>.*?)\)\s*(?P<opts>ENGINE[^;]*);", re.S | re.I)
COL_RE = re.compile(r"^" + BT + r"(?P<col>[^" + BT + r"]+)" + BT + r"\s+(?P<type>[a-zA-Z]+(?:\s*\([^)]*\))?(?:\s+unsigned)?)(?P<rest>.*)$", re.I)
COMMENT_RE = re.compile(r"COMMENT\s*=?\s*'((?:[^'\\]|\\.)*)'", re.I)
DEFAULT_RE = re.compile(r"DEFAULT\s+('(?:[^'\\]|\\.)*'|[^\s,]+)", re.I)


def split_lines(body):
    parts, depth, cur, quote = [], 0, [], False
    for ch in body:
        if ch == "'":
            quote = not quote
        if not quote:
            if ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
            elif ch == "," and depth == 0:
                parts.append("".join(cur).strip()); cur = []; continue
        cur.append(ch)
    if "".join(cur).strip():
        parts.append("".join(cur).strip())
    return [" ".join(p.split()) for p in parts]


def parse(group, path):
    text = path.read_text(encoding="utf-8")
    tables = []
    for m in TABLE_RE.finditer(text):
        name, body, opts = m.group("name"), m.group("body"), m.group("opts")
        tcomment = COMMENT_RE.search(opts)
        cols, keys = [], []
        for line in split_lines(body):
            cm = COL_RE.match(line)
            if cm:
                rest = cm.group("rest")
                c = COMMENT_RE.search(rest)
                d = DEFAULT_RE.search(rest)
                cols.append({
                    "name": cm.group("col"),
                    "type": " ".join(cm.group("type").split()).lower(),
                    "notNull": bool(re.search(r"NOT NULL", rest, re.I)),
                    "default": (d.group(1).strip("'") if d else None),
                    "comment": c.group(1) if c else "",
                })
            else:
                keys.append(re.sub(r"\s*USING BTREE", "", line).replace(BT, ""))
        tables.append({"group": group, "name": name, "comment": tcomment.group(1) if tcomment else "", "columns": cols, "keys": keys})
    return tables


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    all_tables = []
    for group, path in FILES:
        all_tables += parse(group, path)
    (OUT / "jbolt-schema.json").write_text(json.dumps(all_tables, ensure_ascii=False, indent=1), encoding="utf-8")
    lines = ["# JBolt 数据字典（MySQL，自动生成）", "",
             "> 由 tools/jbolt-research/parse_schema.py 解析以下文件生成：" + "、".join(p.name for _, p in FILES), "",
             "| 分组 | 表 | 说明 | 字段数 |", "|---|---|---|---|"]
    for t in all_tables:
        lines.append(f"| {t['group']} | [{t['name']}](#{t['name']}) | {t['comment']} | {len(t['columns'])} |")
    for t in all_tables:
        lines += ["", f"## {t['name']}", "", f"{t['comment']}（分组：{t['group']}）", "",
                  "| 字段 | 类型 | 非空 | 默认值 | 说明 |", "|---|---|---|---|---|"]
        for c in t["columns"]:
            dv = "" if c["default"] is None else c["default"]
            lines.append(f"| {c['name']} | {c['type']} | {'是' if c['notNull'] else ''} | {dv} | {c['comment'].replace('|', '/')} |")
        if t["keys"]:
            lines += ["", "索引：" + "；".join(k.replace('|', '/') for k in t["keys"])]
    (OUT / "03-data-dictionary.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(len(all_tables), "tables,", sum(len(t["columns"]) for t in all_tables), "columns")
    for t in all_tables:
        print(f"{t['group']:16} {t['name']:32} {len(t['columns']):3} {t['comment']}")

main()

