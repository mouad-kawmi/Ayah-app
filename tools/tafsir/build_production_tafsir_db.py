#!/usr/bin/env python3
"""Phase Production 1: builds the real production tafsir databases.

Replaces the test fixtures (tools/tafsir/build_test_tafsir_db.py) with real
production databases generated from the approved sources only. This script is
BUILD-TIME ONLY (no runtime code, no architecture change — see
docs/TafsirDownloadSystem.md, architecture FROZEN).

Approved sources (audit + policy §13.2 — prep/build-time only, never runtime):
  - api.alquran.cloud  (edition ar.muyassar)  -> Muyassar  [documented in §7]
  - QUL / qul.tarteel.ai (open data, mirrored by spa5k/tafsir_api, MIT)
      -> Ibn Kathir, Tabari, Qurtubi, Baghawi, Fath Al-Qadir (classics, PD)

Each database follows the frozen schema contract (§3.2.5):
    tafsir(verse_key TEXT PRIMARY KEY, text TEXT NOT NULL)   -- "surah:ayah"
    meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)
    PRAGMA user_version = 1  (== meta['schema_version'])

Mandatory provenance (§13.1): source, edition, publisher, author, website,
license, copyrightNotice in the catalog AND replicated in meta as source,
edition, publisher, website, copyright_notice.

Output layout (server-ready, URL convention §6.1):
    tools/tafsir/production/site/resources/tafsir/<id>/<version>.db
    tools/tafsir/production/site/resources/catalog.json
    tools/tafsir/production/manifest.json   (machine-readable build report)

Verification (FAIL = build failure):
  - SQLite opens, PRAGMA integrity_check == 'ok'
  - PRAGMA user_version == schemaVersion == 1
  - all required meta keys present and non-empty
  - exactly 6236 verses, no duplicate verse_key, no missing verse
    (missing tafsir text -> '' row, reported as a warning)
  - lookups: first verse, middle verse, last verse

Storage optimization (size audit, storage topic CLOSED — only the free ~3%):
  - per-tafsir PRAGMA page_size (1024 for the classics, 8192 for muyassar)
  - VACUUM as the final build step (freelist -> 0) before SHA-256/size are computed

Usage:
    python tools/tafsir/build_production_tafsir_db.py [--only muyassar] \\
        [--base-url http://localhost:8080] [--no-cache] [--out-dir ...]
"""

import argparse
import datetime
import hashlib
import json
import os
import sqlite3
import sys
import time
import urllib.error
import urllib.request

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

TOOLS_DIR = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(TOOLS_DIR, "..", ".."))
SOURCE_BUNDLED_DB = os.path.join(ROOT, "app", "src", "main", "assets", "tafsir", "muyassar.db")
OUT_ROOT_DEFAULT = os.path.join(TOOLS_DIR, "production")
OUT_ROOT = OUT_ROOT_DEFAULT
SITE_DIR = os.path.join(OUT_ROOT, "site")
DB_DIR = os.path.join(SITE_DIR, "resources", "tafsir")
CACHE_DIR = os.path.join(OUT_ROOT, "cache")
CATALOG_PATH = os.path.join(SITE_DIR, "resources", "catalog.json")
MANIFEST_PATH = os.path.join(OUT_ROOT, "manifest.json")

EXPECTED_VERSE_COUNT = 6236
SCHEMA_VERSION = 1
APP_VERSION = "1.0"  # minAppVersion for every entry (app versionName is "1.0")
USER_AGENT = "qurankotlin-build-tool/2.0"
TIMEOUT_SECONDS = 180
FETCH_RETRIES = 3

ALQURAN_API = "https://api.alquran.cloud/v1/quran/{}"
QUL_BASE = "https://raw.githubusercontent.com/spa5k/tafsir_api/main/tafsir"

REQUIRED_META_KEYS = [
    "id", "schema_version", "name", "name_ar", "name_latin", "author",
    "version", "language", "license", "source", "edition", "publisher",
    "website", "copyright_notice", "generated_at",
]

TAFSIRS = [
    {
        "id": "muyassar",
        "version": "1.0.0",
        "name": "التفسير الميسر",
        "name_latin": "Tafsir Al-Muyassar",
        "author": "مجموعة من العلماء",
        "source": "api.alquran.cloud — edition ar.muyassar (build-time, §7)",
        "edition": "ar.muyassar",
        "publisher": "King Fahd Complex for the Printing of the Holy Quran",
        "website": "https://qurancomplex.gov.sa",
        "license": "Free (Islamic text — King Fahd Complex)",
        "copyright_notice": "التفسير الميسر: إعداد لجنة من العلماء بإشراف مجمع الملك فهد لطباعة المصحف الشريف، المدينة المنورة، المملكة العربية السعودية.",
        "description": "التفسير الميسر — إصدار مجمع الملك فهد لطباعة المصحف الشريف",
        "bundled": True,
        "kind": "alquran",
        "edition_id": "ar.muyassar",
        "page_size": 8192,
    },
    {
        "id": "ibn_kathir",
        "version": "1.0.0",
        "name": "تفسير ابن كثير",
        "name_latin": "Tafsir Ibn Kathir",
        "author": "ابن كثير (إسماعيل بن عمر بن كثير، ت. 774هـ)",
        "source": "QUL — qul.tarteel.ai (open data, via spa5k/tafsir_api mirror, MIT)",
        "edition": "QUL resource tafsir/22 (ar-tafsir-ibn-kathir)",
        "publisher": "QUL — Tarteel AI (compilation numérique)",
        "website": "https://qul.tarteel.ai",
        "license": "Public Domain",
        "copyright_notice": "نص تفسير ابن كثير: مؤلَّف تراثي في الملك العام. التجميع الرقمي: QUL/Tarteel AI (données ouvertes), miroir spa5k/tafsir_api (MIT).",
        "description": "تفسير القرآن العظيم لابن كثير",
        "bundled": False,
        "kind": "qul",
        "slug": "ar-tafsir-ibn-kathir",
        "qul_id": 22,
        "page_size": 1024,
    },
    {
        "id": "tabari",
        "version": "1.0.0",
        "name": "تفسير الطبري",
        "name_latin": "Tafsir At-Tabari",
        "author": "الطبري (محمد بن جرير الطبري، ت. 310هـ)",
        "source": "QUL — qul.tarteel.ai (open data, via spa5k/tafsir_api mirror, MIT)",
        "edition": "QUL resource tafsir/37 (ar-tafsir-al-tabari)",
        "publisher": "QUL — Tarteel AI (compilation numérique)",
        "website": "https://qul.tarteel.ai",
        "license": "Public Domain",
        "copyright_notice": "نص جامع البيان للطبري: مؤلَّف تراثي في الملك العام. التجميع الرقمي: QUL/Tarteel AI (données ouvertes), miroir spa5k/tafsir_api (MIT).",
        "description": "جامع البيان في تأويل آي القرآن للطبري",
        "bundled": False,
        "kind": "qul",
        "slug": "ar-tafsir-al-tabari",
        "qul_id": 37,
        "page_size": 1024,
    },
    {
        "id": "qurtubi",
        "version": "1.0.0",
        "name": "تفسير القرطبي",
        "name_latin": "Tafsir Al-Qurtubi",
        "author": "القرطبي (محمد بن أحمد الأنصاري القرطبي، ت. 671هـ)",
        "source": "QUL — qul.tarteel.ai (open data, via spa5k/tafsir_api mirror, MIT)",
        "edition": "QUL resource tafsir/23 (ar-tafseer-al-qurtubi)",
        "publisher": "QUL — Tarteel AI (compilation numérique)",
        "website": "https://qul.tarteel.ai",
        "license": "Public Domain",
        "copyright_notice": "نص الجامع لأحكام القرآن للقرطبي: مؤلَّف تراثي في الملك العام. التجميع الرقمي: QUL/Tarteel AI (données ouvertes), miroir spa5k/tafsir_api (MIT).",
        "description": "الجامع لأحكام القرآن للقرطبي",
        "bundled": False,
        "kind": "qul",
        "slug": "ar-tafseer-al-qurtubi",
        "qul_id": 23,
        "page_size": 1024,
    },
    {
        "id": "baghawi",
        "version": "1.0.0",
        "name": "تفسير البغوي (معالم التنزيل)",
        "name_latin": "Tafsir Al-Baghawi (Ma'alim at-Tanzil)",
        "author": "البغوي (الحسين بن مسعود البغوي، ت. 516هـ)",
        "source": "QUL — qul.tarteel.ai (open data, via spa5k/tafsir_api mirror, MIT)",
        "edition": "QUL resource tafsir/27 (ar-tafsir-al-baghawi)",
        "publisher": "QUL — Tarteel AI (compilation numérique)",
        "website": "https://qul.tarteel.ai",
        "license": "Public Domain",
        "copyright_notice": "نص معالم التنزيل للبغوي: مؤلَّف تراثي في الملك العام. التجميع الرقمي: QUL/Tarteel AI (données ouvertes), miroir spa5k/tafsir_api (MIT).",
        "description": "معالم التنزيل للبغوي",
        "bundled": False,
        "kind": "qul",
        "slug": "ar-tafsir-al-baghawi",
        "qul_id": 27,
        "page_size": 1024,
    },
    {
        "id": "fath_al_qadir",
        "version": "1.0.0",
        "name": "فتح القدير",
        "name_latin": "Fath Al-Qadir",
        "author": "الشوكاني (محمد بن علي الشوكاني، ت. 1250هـ)",
        "source": "QUL — qul.tarteel.ai (open data, via spa5k/tafsir_api mirror, MIT)",
        "edition": "QUL resource tafsir/494 (fath-al-qadir-al-shawkani)",
        "publisher": "QUL — Tarteel AI (compilation numérique)",
        "website": "https://qul.tarteel.ai",
        "license": "Public Domain",
        "copyright_notice": "نص فتح القدير للشوكاني: مؤلَّف تراثي في الملك العام. التجميع الرقمي: QUL/Tarteel AI (données ouvertes), miroir spa5k/tafsir_api (MIT).",
        "description": "فتح القدير الجامع بين فني الرواية والدراية للشوكاني",
        "bundled": False,
        "kind": "qul",
        "slug": "fath-al-qadir-al-shawkani",
        "qul_id": 494,
        "page_size": 1024,
    },
]


class BuildError(Exception):
    pass


def log(message: str) -> None:
    print(message, flush=True)


def load_canonical_keys() -> list:
    if not os.path.isfile(SOURCE_BUNDLED_DB):
        raise BuildError(f"bundled DB not found: {SOURCE_BUNDLED_DB}")
    connection = sqlite3.connect(f"file:{SOURCE_BUNDLED_DB}?mode=ro", uri=True)
    try:
        rows = connection.execute("SELECT verse_key FROM tafsir").fetchall()
    finally:
        connection.close()
    keys = [row[0] for row in rows]
    if len(keys) != EXPECTED_VERSE_COUNT:
        raise BuildError(f"bundled DB has {len(keys)} verses, expected {EXPECTED_VERSE_COUNT}")
    if len(set(keys)) != len(keys):
        raise BuildError("bundled DB contains duplicate verse keys")
    keys.sort(key=lambda key: (int(key.split(":")[0]), int(key.split(":")[1])))
    return keys


def fetch_bytes(url: str, cache_key: str, use_cache: bool) -> bytes:
    cache_path = os.path.join(CACHE_DIR, cache_key)
    if use_cache and os.path.isfile(cache_path):
        with open(cache_path, "rb") as handle:
            return handle.read()
    last_error = None
    for attempt in range(1, FETCH_RETRIES + 1):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
                data = response.read()
            os.makedirs(CACHE_DIR, exist_ok=True)
            with open(cache_path, "wb") as handle:
                handle.write(data)
            return data
        except (urllib.error.URLError, OSError, TimeoutError) as error:
            last_error = error
            if attempt < FETCH_RETRIES:
                time.sleep(2 * attempt)
    raise BuildError(f"fetch failed after {FETCH_RETRIES} attempts: {url} ({last_error})")


def fetch_alquran(edition_id: str, use_cache: bool) -> dict:
    url = ALQURAN_API.format(edition_id)
    log(f"  fetching: {url} (build-time API)")
    payload = json.loads(fetch_bytes(url, f"alquran-{edition_id}.json", use_cache).decode("utf-8"))
    if payload.get("code") != 200 or payload.get("data") is None:
        raise BuildError(f"alquran.cloud returned {payload.get('code')} {payload.get('status')}")
    return payload["data"]


def fetch_qul(slug: str, use_cache: bool) -> dict:
    log(f"  fetching: {slug} from QUL mirror (114 surah files)")
    texts = {}
    for surah in range(1, 115):
        url = f"{QUL_BASE}/{slug}/{surah}.json"
        payload = json.loads(fetch_bytes(url, f"qul-{slug}-{surah:03d}.json", use_cache).decode("utf-8"))
        for ayah in payload:
            surah_number = int(ayah["surah"])
            ayah_number = int(ayah["ayah"])
            verse_key = f"{surah_number}:{ayah_number}"
            if verse_key in texts:
                raise BuildError(f"{slug}: duplicate verse key in source: {verse_key}")
            texts[verse_key] = ayah["text"]
        if surah % 20 == 0:
            log(f"    ... {surah}/114 surahs fetched")
    return texts


def normalize_text(raw) -> str:
    if raw is None:
        return ""
    return str(raw).replace("\r\n", "\n").strip()


def build_database(tafsir: dict, texts: dict, canonical_keys: list) -> tuple:
    out_path = os.path.join(DB_DIR, tafsir["id"], f"{tafsir['version']}.db")
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    if os.path.exists(out_path):
        os.remove(out_path)

    connection = sqlite3.connect(out_path)
    try:
        connection.execute(f"PRAGMA page_size = {tafsir['page_size']}")
        connection.execute("PRAGMA journal_mode=OFF")
        connection.execute("PRAGMA synchronous=OFF")
        connection.execute("CREATE TABLE tafsir (verse_key TEXT PRIMARY KEY, text TEXT NOT NULL)")
        connection.execute("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")

        empty_count = 0
        connection.executemany(
            "INSERT INTO tafsir (verse_key, text) VALUES (?, ?)",
            ((key, texts.get(key, "")) for key in canonical_keys),
        )
        empty_count = sum(1 for key in canonical_keys if not texts.get(key, "").strip())

        generated_at = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        meta_rows = [
            ("id", tafsir["id"]),
            ("schema_version", str(SCHEMA_VERSION)),
            ("name", tafsir["name"]),
            ("name_ar", tafsir["name"]),
            ("name_latin", tafsir["name_latin"]),
            ("author", tafsir["author"]),
            ("version", tafsir["version"]),
            ("language", "ar"),
            ("license", tafsir["license"]),
            ("source", tafsir["source"]),
            ("edition", tafsir["edition"]),
            ("publisher", tafsir["publisher"]),
            ("website", tafsir["website"]),
            ("copyright_notice", tafsir["copyright_notice"]),
            ("generated_at", generated_at),
        ]
        connection.executemany("INSERT INTO meta (key, value) VALUES (?, ?)", meta_rows)
        connection.execute(f"PRAGMA user_version = {SCHEMA_VERSION}")
        connection.commit()
        connection.execute("VACUUM")
    finally:
        connection.close()
    return out_path, empty_count, generated_at


def verify_database(tafsir: dict, out_path: str, canonical_keys: list, empty_count: int) -> None:
    connection = sqlite3.connect(f"file:{out_path}?mode=ro", uri=True)
    try:
        integrity = connection.execute("PRAGMA integrity_check").fetchone()[0]
        if integrity != "ok":
            raise BuildError(f"{tafsir['id']}: integrity_check = {integrity!r}")
        log("  Integrity OK")

        user_version = connection.execute("PRAGMA user_version").fetchone()[0]
        if user_version != SCHEMA_VERSION:
            raise BuildError(f"{tafsir['id']}: user_version = {user_version}, expected {SCHEMA_VERSION}")
        log(f"  Schema OK (user_version={user_version})")

        meta_rows = dict(connection.execute("SELECT key, value FROM meta").fetchall())
        for key in REQUIRED_META_KEYS:
            if key not in meta_rows or not str(meta_rows[key]).strip():
                raise BuildError(f"{tafsir['id']}: missing or empty meta key '{key}'")
        if meta_rows["schema_version"] != str(SCHEMA_VERSION):
            raise BuildError(f"{tafsir['id']}: meta schema_version = {meta_rows['schema_version']}")
        log(f"  Metadata OK ({len(REQUIRED_META_KEYS)} required keys, provenance complete)")

        count = connection.execute("SELECT COUNT(*) FROM tafsir").fetchone()[0]
        if count != EXPECTED_VERSE_COUNT:
            raise BuildError(f"{tafsir['id']}: {count} verses, expected {EXPECTED_VERSE_COUNT}")
        distinct = connection.execute("SELECT COUNT(DISTINCT verse_key) FROM tafsir").fetchone()[0]
        if distinct != EXPECTED_VERSE_COUNT:
            raise BuildError(f"{tafsir['id']}: {distinct} distinct verse keys, expected {EXPECTED_VERSE_COUNT}")
        stored = {row[0] for row in connection.execute("SELECT verse_key FROM tafsir").fetchall()}
        if stored != set(canonical_keys):
            missing = set(canonical_keys) - stored
            extra = stored - set(canonical_keys)
            raise BuildError(
                f"{tafsir['id']}: verse set mismatch (missing={len(missing)}, extra={len(extra)})"
            )
        log(f"  Verses OK ({count} verses, no duplicates, no missing)")

        middle_key = canonical_keys[len(canonical_keys) // 2]
        for label, key in (("first", canonical_keys[0]), ("middle", middle_key), ("last", canonical_keys[-1])):
            row = connection.execute(
                "SELECT verse_key, text FROM tafsir WHERE verse_key = ?", (key,)
            ).fetchone()
            if row is None:
                raise BuildError(f"{tafsir['id']}: lookup failed for {label} verse {key}")
            log(f"  Lookup OK ({label} verse {key}, text={len(row[1])} chars)")
    finally:
        connection.close()

    if empty_count:
        log(f"  WARNING: {empty_count} verses have empty tafsir text (filled with '')")


def file_sha256(path: str) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def build_tafsir(tafsir: dict, canonical_keys: list, use_cache: bool) -> dict:
    t0 = time.perf_counter()
    log(f"Generating {tafsir['name']} ({tafsir['id']})...")
    if tafsir["kind"] == "alquran":
        data = fetch_alquran(tafsir["edition_id"], use_cache)
        texts = {}
        for surah in data["surahs"]:
            for ayah in surah["ayahs"]:
                verse_key = f"{surah['number']}:{ayah['numberInSurah']}"
                if verse_key in texts:
                    raise BuildError(f"{tafsir['id']}: duplicate verse key in source: {verse_key}")
                texts[verse_key] = ayah["text"]
    elif tafsir["kind"] == "qul":
        texts = fetch_qul(tafsir["slug"], use_cache)
    else:
        raise BuildError(f"unknown source kind: {tafsir['kind']}")

    texts = {key: normalize_text(text) for key, text in texts.items()}
    extra = set(texts) - set(canonical_keys)
    if extra:
        raise BuildError(f"{tafsir['id']}: source contains {len(extra)} keys outside the canonical 6236")

    out_path, empty_count, generated_at = build_database(tafsir, texts, canonical_keys)
    verify_database(tafsir, out_path, canonical_keys, empty_count)

    sha256 = file_sha256(out_path)
    size_bytes = os.path.getsize(out_path)
    duration = round(time.perf_counter() - t0, 2)
    log(f"  sha256: {sha256}")
    log(f"  size:   {size_bytes} bytes ({size_bytes / 1024 / 1024:.2f} MiB)")
    log(f"  version: {tafsir['version']}")
    log(f"  Finished ({duration}s)")
    return {
        "out_path": out_path,
        "sha256": sha256,
        "size_bytes": size_bytes,
        "duration_s": duration,
        "empty_count": empty_count,
        "generated_at": generated_at,
    }


def write_catalog(results: dict, base_url: str, generated_at: str) -> None:
    resources = []
    for tafsir in TAFSIRS:
        result = results.get(tafsir["id"])
        if result is None:
            continue
        base = base_url.rstrip("/")
        resources.append({
            "id": tafsir["id"],
            "type": "TAFSIR",
            "name": tafsir["name"],
            "nameLatin": tafsir["name_latin"],
            "author": tafsir["author"],
            "license": tafsir["license"],
            "source": tafsir["source"],
            "edition": tafsir["edition"],
            "publisher": tafsir["publisher"],
            "website": tafsir["website"],
            "copyrightNotice": tafsir["copyright_notice"],
            "version": tafsir["version"],
            "language": "ar",
            "lastUpdated": result["generated_at"],
            "minAppVersion": APP_VERSION,
            "schemaVersion": SCHEMA_VERSION,
            "downloadSizeBytes": result["size_bytes"],
            "installedSizeBytes": result["size_bytes"],
            "downloadUrl": f"{base}/resources/tafsir/{tafsir['id']}/{tafsir['version']}.db",
            "sha256": result["sha256"],
            "bundled": tafsir["bundled"],
            "description": tafsir["description"],
        })
    catalog = {
        "schemaVersion": 1,
        "generatedAt": generated_at,
        "resources": resources,
    }
    os.makedirs(os.path.dirname(CATALOG_PATH), exist_ok=True)
    with open(CATALOG_PATH, "w", encoding="utf-8") as handle:
        json.dump(catalog, handle, ensure_ascii=False, indent=2)
    log(f"OK: {CATALOG_PATH} ({len(resources)} entries)")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", action="append", default=[], help="build only these ids (repeatable)")
    parser.add_argument("--base-url", default="http://localhost:8080",
                        help="BASE URL written into catalog downloadUrl (owner sets real host before deploy)")
    parser.add_argument("--no-cache", action="store_true", help="ignore the fetch cache")
    parser.add_argument("--out-dir", default=OUT_ROOT_DEFAULT, help="output root (default: tools/tafsir/production)")
    args = parser.parse_args()

    global OUT_ROOT, SITE_DIR, DB_DIR, CACHE_DIR, CATALOG_PATH, MANIFEST_PATH
    OUT_ROOT = os.path.abspath(args.out_dir)
    SITE_DIR = os.path.join(OUT_ROOT, "site")
    DB_DIR = os.path.join(SITE_DIR, "resources", "tafsir")
    CACHE_DIR = os.path.join(OUT_ROOT, "cache")
    CATALOG_PATH = os.path.join(SITE_DIR, "resources", "catalog.json")
    MANIFEST_PATH = os.path.join(OUT_ROOT, "manifest.json")

    selected = TAFSIRS if not args.only else [t for t in TAFSIRS if t["id"] in args.only]
    if not selected:
        print(f"ERROR: --only matched no tafsir ids (known: {[t['id'] for t in TAFSIRS]})", file=sys.stderr)
        return 1

    start = time.perf_counter()
    generated_at = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    try:
        canonical_keys = load_canonical_keys()
    except BuildError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    log(f"canonical verse keys: {len(canonical_keys)} (from bundled {SOURCE_BUNDLED_DB})")
    log(f"building {len(selected)} tafsirs -> {OUT_ROOT}")

    results = {}
    warnings = []
    for tafsir in selected:
        try:
            result = build_tafsir(tafsir, canonical_keys, use_cache=not args.no_cache)
            results[tafsir["id"]] = result
            if result["empty_count"]:
                warnings.append(f"{tafsir['id']}: {result['empty_count']} verses without tafsir text (empty strings)")
        except BuildError as error:
            print(f"ERROR [{tafsir['id']}]: {error}", file=sys.stderr)
            return 1

    write_catalog(results, args.base_url, generated_at)
    total_duration = round(time.perf_counter() - start, 2)

    manifest = {
        "tool": "tools/tafsir/build_production_tafsir_db.py",
        "generatedAt": generated_at,
        "python": sys.version.split()[0],
        "baseUrl": args.base_url,
        "schemaVersion": SCHEMA_VERSION,
        "appVersion": APP_VERSION,
        "canonicalVerseCount": len(canonical_keys),
        "builds": [
            {
                "id": t["id"],
                "version": t["version"],
                "source": t["source"],
                "edition": t["edition"],
                "sha256": results[t["id"]]["sha256"],
                "sizeBytes": results[t["id"]]["size_bytes"],
                "installedSizeBytes": results[t["id"]]["size_bytes"],
                "durationS": results[t["id"]]["duration_s"],
                "emptyVerseCount": results[t["id"]]["empty_count"],
                "file": os.path.relpath(results[t["id"]]["out_path"], OUT_ROOT),
            }
            for t in selected
        ],
        "totalDurationS": total_duration,
        "warnings": warnings,
    }
    with open(MANIFEST_PATH, "w", encoding="utf-8") as handle:
        json.dump(manifest, handle, ensure_ascii=False, indent=2)
    log(f"OK: {MANIFEST_PATH}")

    log("")
    log("=== Build summary ===")
    log(f"{'id':<14}{'version':<9}{'size MiB':>10}{'sha256 (prefix)':>22}{'empty':>7}{'duration s':>11}")
    for t in selected:
        r = results[t["id"]]
        log(f"{t['id']:<14}{t['version']:<9}{r['size_bytes'] / 1024 / 1024:>10.2f}{r['sha256'][:16]:>22}{r['empty_count']:>7}{r['duration_s']:>11.2f}")
    log(f"total build duration: {total_duration}s")
    if warnings:
        log("")
        log("Warnings:")
        for warning in warnings:
            log(f"  - {warning}")
    log("")
    log("NOTE: catalog downloadUrl uses base-url placeholder. Set the real hosting")
    log("BASE (BuildConfig.RESOURCE_BASE_URL counterpart) before Phase 2 deployment.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
