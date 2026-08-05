#!/usr/bin/env python3
"""Independent Phase Production 1 verification (post-build audit).

Checks each generated DB a second time, from raw SQLite, independently of the
builder: row counts, empty texts, random verse lookups, first/middle/last,
meta completeness, and Muyassar prod-vs-bundled content diff.
"""
import os
import random
import sqlite3
import sys

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ROOT = os.path.dirname(os.path.abspath(__file__))
PROD = os.path.join(ROOT, "production", "site", "resources", "tafsir")
BUNDLED = os.path.normpath(os.path.join(ROOT, "..", "..", "app", "src", "main", "assets", "tafsir", "muyassar.db"))
IDS = ["muyassar", "ibn_kathir", "tabari", "qurtubi", "baghawi", "fath_al_qadir"]
REQUIRED_META = ["id", "schema_version", "name", "name_ar", "name_latin", "author",
                 "version", "language", "license", "source", "edition", "publisher",
                 "website", "copyright_notice", "generated_at"]

failures = []

for tafsir_id in IDS:
    path = os.path.join(PROD, tafsir_id, "1.0.0.db")
    if not os.path.isfile(path):
        failures.append(f"{tafsir_id}: missing {path}")
        continue
    conn = sqlite3.connect(f"file:{path}?mode=ro", uri=True)
    try:
        count = conn.execute("SELECT COUNT(*) FROM tafsir").fetchone()[0]
        distinct = conn.execute("SELECT COUNT(DISTINCT verse_key) FROM tafsir").fetchone()[0]
        integrity = conn.execute("PRAGMA integrity_check").fetchone()[0]
        user_version = conn.execute("PRAGMA user_version").fetchone()[0]
        meta = dict(conn.execute("SELECT key, value FROM meta").fetchall())
        missing_meta = [k for k in REQUIRED_META if k not in meta or not str(meta[k]).strip()]
        empty = conn.execute("SELECT verse_key FROM tafsir WHERE trim(text) = ''").fetchall()
        first = conn.execute("SELECT text FROM tafsir WHERE verse_key='1:1'").fetchone()
        middle = conn.execute("SELECT text FROM tafsir WHERE verse_key='26:187'").fetchone()
        last = conn.execute("SELECT text FROM tafsir WHERE verse_key='114:6'").fetchone()
        random.seed(2026)
        probes = [f"{random.randint(1, 114)}:{random.randint(1, 200)}" for _ in range(20)]
        rows = conn.execute("SELECT verse_key FROM tafsir").fetchall()
        key_set = {r[0] for r in rows}
        found = sum(1 for p in probes if p in key_set)
        if count != 6236:
            failures.append(f"{tafsir_id}: count={count}")
        if distinct != 6236:
            failures.append(f"{tafsir_id}: distinct={distinct}")
        if integrity != "ok":
            failures.append(f"{tafsir_id}: integrity={integrity}")
        if user_version != 1:
            failures.append(f"{tafsir_id}: user_version={user_version}")
        if missing_meta:
            failures.append(f"{tafsir_id}: missing meta {missing_meta}")
        if first is None or middle is None or last is None:
            failures.append(f"{tafsir_id}: first/middle/last lookup failed")
        print(f"{tafsir_id:<14} verses={count:<5} distinct={distinct:<5} integrity={integrity} "
              f"user_version={user_version} empty={len(empty)} ({[r[0] for r in empty][:4]}) "
              f"probe_hits={found}/20 meta_ok={not missing_meta}")
    finally:
        conn.close()

print()
bundled = sqlite3.connect(f"file:{BUNDLED}?mode=ro", uri=True)
prod_m = sqlite3.connect(f"file:{os.path.join(PROD, 'muyassar', '1.0.0.db')}?mode=ro", uri=True)
bundled_texts = dict(bundled.execute("SELECT verse_key, text FROM tafsir").fetchall())
prod_texts = dict(prod_m.execute("SELECT verse_key, text FROM tafsir").fetchall())
diff = [k for k in bundled_texts if bundled_texts[k] != prod_texts.get(k)]
print(f"muyassar prod vs bundled asset: content-diff verses = {len(diff)}")
if diff:
    print("  sample diffs:", diff[:3])
    for k in diff[:1]:
        print("  bundled:", repr(bundled_texts[k][:120]))
        print("  prod:   ", repr(prod_texts[k][:120]))

print()
if failures:
    print(f"FAILURES ({len(failures)}):")
    for f in failures:
        print("  -", f)
    sys.exit(1)
print("ALL CHECKS PASSED")
