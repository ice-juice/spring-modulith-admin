# -*- coding: utf-8 -*-
"""Minimal Java class-file reader: dump classes, members and string constants from jbolt_core.jar."""
import json, struct, zipfile, collections
from pathlib import Path
from paths import JBOLT_HOME, JBOLT_RUN_HOME, RESEARCH_DOCS  # noqa: E402

JAR = JBOLT_HOME / "lib" / "jbolt_core.jar"
OUT = RESEARCH_DOCS


def read_class(data):
    pos = 8
    (count,) = struct.unpack_from(">H", data, pos); pos += 2
    cp = [None] * count
    i = 1
    while i < count:
        tag = data[pos]; pos += 1
        if tag == 1:
            (ln,) = struct.unpack_from(">H", data, pos); pos += 2
            cp[i] = ("utf8", data[pos:pos + ln].decode("utf-8", "replace")); pos += ln
        elif tag in (3, 4):
            pos += 4
        elif tag in (5, 6):
            pos += 8; i += 1
        elif tag == 7:
            cp[i] = ("class", struct.unpack_from(">H", data, pos)[0]); pos += 2
        elif tag == 8:
            cp[i] = ("string", struct.unpack_from(">H", data, pos)[0]); pos += 2
        elif tag in (9, 10, 11, 12, 17, 18):
            pos += 4
        elif tag == 15:
            pos += 3
        elif tag in (16, 19, 20):
            pos += 2
        else:
            raise ValueError(f"bad tag {tag}")
        i += 1
    u = lambda idx: cp[idx][1] if cp[idx] and cp[idx][0] == "utf8" else ""
    cname = lambda idx: u(cp[idx][1]) if idx and cp[idx] else ""
    access, this_c, super_c = struct.unpack_from(">HHH", data, pos); pos += 6
    (ic,) = struct.unpack_from(">H", data, pos); pos += 2
    ifaces = [cname(struct.unpack_from(">H", data, pos + 2 * k)[0]) for k in range(ic)]; pos += 2 * ic

    def members():
        nonlocal pos
        (n,) = struct.unpack_from(">H", data, pos); pos += 2
        res = []
        for _ in range(n):
            acc, ni, di, ac = struct.unpack_from(">HHHH", data, pos); pos += 8
            for _ in range(ac):
                (_, al) = struct.unpack_from(">HI", data, pos); pos += 6 + al
            res.append({"name": u(ni), "desc": u(di), "access": acc})
        return res

    fields = members(); methods = members()
    strings = sorted({u(e[1]) for e in cp if e and e[0] == "string"})
    return {"name": cname(this_c).replace("/", "."), "super": cname(super_c).replace("/", "."), "interfaces": [x.replace("/", ".") for x in ifaces],
            "access": access, "fields": fields, "methods": methods, "strings": strings}


def short(desc):
    import re
    return re.sub(r"L([\w/$]+);", lambda m: m.group(1).split("/")[-1], desc)


def main():
    classes, resources = [], []
    with zipfile.ZipFile(JAR) as z:
        for info in z.infolist():
            if info.filename.endswith(".class"):
                classes.append(read_class(z.read(info)))
            elif not info.is_dir():
                resources.append((info.filename, info.file_size))
    (OUT / "jbolt-core-classes.json").write_text(json.dumps(classes, ensure_ascii=False), encoding="utf-8")
    top = [c for c in classes if "$" not in c["name"]]
    by_pkg = collections.defaultdict(list)
    for c in top:
        by_pkg[c["name"].rsplit(".", 1)[0]].append(c)
    lines = ["# jbolt_core.jar 类与方法清单（自动生成）", "",
             f"> 由 tools/jbolt-research/dump_core_jar.py 直接解析 class 文件生成。共 {len(classes)} 个 class（顶层 {len(top)} 个），{len(resources)} 个资源文件。", "",
             "## 资源文件", ""] + [f"- {n} ({s} B)" for n, s in resources]
    for pkg in sorted(by_pkg):
        lines += ["", f"## {pkg}"]
        for c in sorted(by_pkg[pkg], key=lambda x: x["name"]):
            pub = [m for m in c["methods"] if m["access"] & 0x0001 and m["name"] not in ("<clinit>",)]
            kind = "interface" if c["access"] & 0x0200 else ("enum" if c["super"] == "java.lang.Enum" else ("annotation" if c["access"] & 0x2000 else "class"))
            lines += ["", f"### {c['name'].rsplit('.', 1)[1]} ({kind}) extends {c['super'].rsplit('.', 1)[-1]}" + (f" implements {', '.join(i.rsplit('.', 1)[-1] for i in c['interfaces'])}" if c['interfaces'] else "")]
            if pub:
                lines.append("")
                lines.append("方法：" + "；".join(sorted({m['name'] + short(m['desc']).split(')')[0] + ')' for m in pub})))
            consts = [f for f in c["fields"] if (f["access"] & 0x0018) == 0x0018 and f["access"] & 0x0001]
            if consts:
                lines.append("")
                lines.append("常量：" + "、".join(f["name"] for f in consts[:80]))
    (OUT / "jbolt-core-api.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(len(classes), len(top), len(resources))
    for n, s in resources:
        print("RES", n, s)

main()

