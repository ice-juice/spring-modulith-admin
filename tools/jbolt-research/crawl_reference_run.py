# -*- coding: utf-8 -*-
"""HTTP smoke crawl of the JBolt reference run: log in with the default local admin and request every menu page."""
import hashlib, http.cookiejar, json, re, subprocess, sys, urllib.parse, urllib.request
from pathlib import Path
from paths import JBOLT_HOME, JBOLT_RUN_HOME, RESEARCH_DOCS  # noqa: E402

BASE = "http://127.0.0.1:8090/"
MYSQL = Path.home() / "AppData/Local/Programs/mysql-8.4.10-winx64/bin/mysql.exe"
OUT = RESEARCH_DOCS / "jbolt-runtime-crawl.json"


def sql(q):
    r = subprocess.run([str(MYSQL), "-uroot", "-proot", "--host=127.0.0.1", "--default-character-set=utf8mb4", "-N", "-B", "jboltai", "-e", q],
                       capture_output=True, text=True, encoding="utf-8")
    return [line.split("\t") for line in r.stdout.strip().splitlines() if line]


jar = http.cookiejar.CookieJar()
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))


def req(path, data=None, headers=None):
    body = urllib.parse.urlencode(data).encode() if data is not None else None
    r = urllib.request.Request(BASE + path.lstrip("/"), data=body, headers=headers or {})
    try:
        with opener.open(r, timeout=30) as resp:
            return resp.status, resp.headers.get("Content-Type", ""), resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.headers.get("Content-Type", ""), e.read().decode("utf-8", "replace")


req("admin/login")
pwd = "ab" + hashlib.md5(b"123").hexdigest() + "cde"   # JBolt login format: 2 random chars + md5 + 3 random chars
st, ct, body = req("admin/login", {"username": "admin", "password": pwd}, {"X-Requested-With": "XMLHttpRequest"})
print("login", st, body[:120])
if '"state":"ok"' not in body.replace(" ", ""):
    sys.exit("login failed")

ERR = re.compile(r"Exception|无权访问|系统异常|500 Internal|404 Not Found|找不到|Error 500", re.I)
results = []
menus = sql("select id, pid, title, permission_key, url, is_menu, open_type, permission_level from jb_permission order by permission_level, sort_rank")
for pid_, ppid, title, key, url, is_menu, open_type, level in menus:
    item = {"title": title, "key": key, "url": url if url != "NULL" else "", "isMenu": is_menu == "1", "openType": open_type, "level": int(level)}
    if item["url"]:
        u = item["url"].lstrip("/")
        st1, ct1, b1 = req(u)
        st2, ct2, b2 = req(u, headers={"X-PJAX": "true", "X-Requested-With": "XMLHttpRequest"})
        h1 = re.search(r"<h1[^>]*>(.*?)</h1>", b2, re.S)
        item.update({"normal": st1, "pjax": st2, "pjaxBytes": len(b2), "heading": re.sub(r"<[^>]+>|\s+", " ", h1.group(1)).strip() if h1 else "",
                     "error": bool(ERR.search(b2)) and not item["key"].startswith("druid"), "errorSnippet": (ERR.search(b2).group(0) if ERR.search(b2) else "")})
        st3, ct3, b3 = req(u + "/datas", headers={"X-Requested-With": "XMLHttpRequest"})
        if st3 == 200 and "json" in ct3:
            try:
                j = json.loads(b3)
                d = j.get("data")
                item["datas"] = d.get("totalRow") if isinstance(d, dict) and "totalRow" in d else (len(d) if isinstance(d, list) else None)
            except Exception:
                pass
    results.append(item)
    print(f"{'  ' * (int(level) - 1)}{title} [{key}] {item.get('url', '')} normal={item.get('normal')} pjax={item.get('pjax')} h1={item.get('heading', '')!r} err={item.get('errorSnippet', '')} datas={item.get('datas')}")

OUT.write_text(json.dumps(results, ensure_ascii=False, indent=1), encoding="utf-8")
st, ct, body = req("admin/logout")
print("logout", st)

