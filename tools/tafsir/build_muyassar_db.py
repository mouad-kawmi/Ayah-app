#!/usr/bin/env python3
"""Build-time tool: generates the bundled muyassar.db SQLite database.

Phase 1 of the Tafsir Download System (see docs/TafsirDownloadSystem.md).
This script runs ONLY at build time on the developer machine. It fetches the
same alquran.cloud edition ("ar.muyassar") that the legacy in-app downloader
used, so the bundled content is identical to the previous behavior.

Usage:
    python tools/tafsir/build_muyassar_db.py

Output:
    app/src/main/assets/tafsir/muyassar.db

Schema (contract §3.2.5 of docs/TafsirDownloadSystem.md):
    tafsir(verse_key TEXT PRIMARY KEY, text TEXT NOT NULL)   -- "surah:ayah" keys
    meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)          -- required keys below
    PRAGMA user_version = 1 (schema version, must equal meta['schema_version'])
"""

import datetime
import hashlib
import json
import os
import sqlite3
import sys
import urllib.request

EDITION_ID = "ar.muyassar"
API_URL = f"https://api.alquran.cloud/v1/quran/{EDITION_ID}"
OUT_PATH = os.path.normpath(
    os.path.join(
        os.path.dirname(os.path.abspath(__file__)),
        "..", "..", "app", "src", "main", "assets", "tafsir", "muyassar.db",
    )
)

REQUIRED_META = {
    "schema_version": "1",
    "name": "التفسير الميسر",
    "name_ar": "التفسير الميسر",
    "author": "مجموعة من العلماء",
    "version": "1.0.0",
    "language": "ar",
    "license": "Free",
    "source": "api.alquran.cloud edition ar.muyassar",
}


def fetch_edition() -> dict:
    request = urllib.request.Request(API_URL, headers={"User-Agent": "qurankotlin-build-tool/1.0"})
    with urllib.request.urlopen(request, timeout=120) as response:
        return json.load(response)


def main() -> int:
    payload = fetch_edition()
    if payload.get("code") != 200 or payload.get("data") is None:
        print(f"ERROR: API returned {payload.get('code')} {payload.get('status')}", file=sys.stderr)
        return 1

    os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)

    connection = sqlite3.connect(OUT_PATH)
    try:
        connection.execute("PRAGMA journal_mode=OFF")
        connection.execute("PRAGMA synchronous=OFF")
        connection.execute("CREATE TABLE tafsir (verse_key TEXT PRIMARY KEY, text TEXT NOT NULL)")
        connection.execute("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")

        verse_count = 0
        for surah in payload["data"]["surahs"]:
            for ayah in surah["ayahs"]:
                verse_key = f"{surah['number']}:{ayah['numberInSurah']}"
                connection.execute(
                    "INSERT INTO tafsir (verse_key, text) VALUES (?, ?)",
                    (verse_key, ayah["text"]),
                )
                verse_count += 1

        meta_rows = list(REQUIRED_META.items()) + [
            ("generated_at", datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"))
        ]
        connection.executemany("INSERT INTO meta (key, value) VALUES (?, ?)", meta_rows)
        connection.execute("PRAGMA user_version = 1")
        connection.commit()
    finally:
        connection.close()

    with open(OUT_PATH, "rb") as db_file:
        digest = hashlib.sha256(db_file.read()).hexdigest()

    size_bytes = os.path.getsize(OUT_PATH)
    print(f"OK: {OUT_PATH}")
    print(f"    verses: {verse_count}")
    print(f"    size:   {size_bytes} bytes ({size_bytes / 1024 / 1024:.2f} MiB)")
    print(f"    sha256: {digest}")
    print("    meta:   schema_version=1, user_version=1")
    return 0


if __name__ == "__main__":
    sys.exit(main())
