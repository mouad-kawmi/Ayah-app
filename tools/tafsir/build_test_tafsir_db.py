#!/usr/bin/env python3
"""Phase 2 test tool: builds a downloadable test tafsir database.

Generates tools/tafsir/site/resources/tafsir/ibn_kathir.db (same schema contract
as the bundled muyassar, see docs/TafsirDownloadSystem.md §3.2.5) plus the
catalog.json that the app downloads from the local test server
(http://10.0.2.2:8080/ in debug builds).

The SHA-256 in the catalog is the authoritative integrity value: the download
manager verifies the downloaded file against it before install. The meta table
does not carry a self-hash (a file cannot contain its own hash), matching the
bundled DB which has no sha256 meta row either.

Usage:
    python tools/tafsir/build_test_tafsir_db.py [--version 1.0.0] [--text-marker v1]
"""

import argparse
import datetime
import hashlib
import json
import os
import sqlite3
import sys

ROOT = os.path.normpath(
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
)
SOURCE_DB = os.path.join(ROOT, "app", "src", "main", "assets", "tafsir", "muyassar.db")
SITE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "site")
RESOURCES_DIR = os.path.join(SITE_DIR, "resources")
OUT_PATH = os.path.join(RESOURCES_DIR, "tafsir", "ibn_kathir.db")
CATALOG_PATH = os.path.join(RESOURCES_DIR, "catalog.json")

RESOURCE_ID = "ibn_kathir"
META = {
    "schema_version": "1",
    "name": "تفسير ابن كثير (نسخة تجريبية)",
    "name_ar": "تفسير ابن كثير (نسخة تجريبية)",
    "name_latin": "Tafsir Ibn Kathir (test)",
    "author": "إسماعيل بن كثير",
    "version": "1.0.0",
    "language": "ar",
    "license": "Free (test fixture)",
    "source": "local test server (tools/tafsir)",
}


def verse_keys_from_bundled() -> list:
    connection = sqlite3.connect(SOURCE_DB)
    try:
        rows = connection.execute("SELECT verse_key FROM tafsir ORDER BY verse_key").fetchall()
        return [row[0] for row in rows]
    finally:
        connection.close()


def build_db(keys: list, version: str, text_marker: str) -> None:
    os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
    if os.path.exists(OUT_PATH):
        os.remove(OUT_PATH)
    connection = sqlite3.connect(OUT_PATH)
    try:
        connection.execute("PRAGMA journal_mode=OFF")
        connection.execute("PRAGMA synchronous=OFF")
        connection.execute("CREATE TABLE tafsir (verse_key TEXT PRIMARY KEY, text TEXT NOT NULL)")
        connection.execute("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        for key in keys:
            connection.execute(
                "INSERT INTO tafsir (verse_key, text) VALUES (?, ?)",
                (key, f"تفسير ابن كثير — نص تجريبي للآية {key} (marker {text_marker})"),
            )
        meta_rows = dict(META) | {"version": version}
        meta_rows = list(meta_rows.items()) + [
            ("id", RESOURCE_ID),
            ("generated_at", datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")),
        ]
        connection.executemany("INSERT INTO meta (key, value) VALUES (?, ?)", meta_rows)
        connection.execute("PRAGMA user_version = 1")
        connection.commit()
    finally:
        connection.close()


def file_sha256(path: str) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--version", default="1.0.0", help="resource version written into meta + catalog")
    parser.add_argument("--text-marker", default="v1", help="distinguishing text appended to each verse")
    parser.add_argument("--min-app-version", default="1.0", help="minimum app version required by the catalog entry")
    parser.add_argument("--schema-version", default="1", help="catalog + DB schema version")
    args = parser.parse_args()

    if not os.path.isfile(SOURCE_DB):
        print(f"ERROR: bundled DB not found: {SOURCE_DB}", file=sys.stderr)
        return 1
    keys = verse_keys_from_bundled()
    print(f"verse keys from bundled DB: {len(keys)}")

    build_db(keys, args.version, args.text_marker)
    sha = file_sha256(OUT_PATH)
    size = os.path.getsize(OUT_PATH)
    catalog = {
        "schemaVersion": 1,
        "generatedAt": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "resources": [
            {
                "id": RESOURCE_ID,
                "type": "TAFSIR",
                "name": META["name"],
                "nameLatin": META["name_latin"],
                "author": META["author"],
                "license": META["license"],
                "version": args.version,
                "language": "ar",
                "lastUpdated": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
                "minAppVersion": args.min_app_version,
                "schemaVersion": int(args.schema_version),
                "downloadSizeBytes": size,
                "installedSizeBytes": size,
                "downloadUrl": "http://10.0.2.2:8080/resources/tafsir/ibn_kathir.db",
                "sha256": sha,
                "bundled": False,
                "description": "Test fixture for Phase 2 verification",
            }
        ],
    }
    os.makedirs(os.path.dirname(CATALOG_PATH), exist_ok=True)
    with open(CATALOG_PATH, "w", encoding="utf-8") as handle:
        json.dump(catalog, handle, ensure_ascii=False, indent=2)

    print(f"OK: {OUT_PATH}")
    print(f"    size:   {size} bytes ({size / 1024 / 1024:.2f} MiB)")
    print(f"    sha256: {sha}")
    print(f"OK: {CATALOG_PATH}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
