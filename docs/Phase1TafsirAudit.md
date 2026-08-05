# Phase 1 Audit Report — Bundled Tafsir Al-Muyassar (SQLite)

Date: 2026-08-02
Status: PASSED — all acceptance criteria verified

Scope: Phase 1 of the frozen Tafsir Download System (docs/TafsirDownloadSystem.md).
No Phase 2 work was performed. No downloads, catalog, manager screen, or network
requests were added. The architecture document was not modified.

---

## 1. Deliverables

| Artifact | Path | Status |
|---|---|---|
| Bundled DB (2.78 MiB, 6236 verses) | `app/src/main/assets/tafsir/muyassar.db` | Generated |
| Build-time generator script | `tools/tafsir/build_muyassar_db.py` | Runs, reproducible |
| TafsirReader (copy / integrity / self-heal / cache / logs) | `app/src/main/java/com/example/quranapp/data/tafsir/TafsirReader.kt` | Implemented |
| TafsirSelectionStore | `app/src/main/java/com/example/quranapp/data/tafsir/TafsirSelectionStore.kt` | Implemented |
| Preference `selected_tafsir_id` | `QuranPreferences.kt` | Implemented |
| TAFSIR log category + 9xxx error codes | `LogCategory.kt`, `ErrorCode.kt` | Added |
| Muyassar tab merged into detail sheet (SQLite-backed) | `SurahDetailScreen.kt` | Implemented |
| `ar.muyassar` removed from translation editions | `TranslationRepository.kt` | Done |
| Dead `TafsirManager.kt` removed | `core/utils/` | Deleted |

Bundled DB fingerprint:
- sha256: `c59dcfdb9714f64c56610aabce22dab37bf7c82eed7ff1edc1782d48011ef1f4`
- `PRAGMA user_version = 1`, `meta.schema_version = 1`
- Tables: `tafsir(verse_key TEXT PK, text TEXT)`, `meta(key PK, value)`
- `meta` contains all required keys (schema_version, name, name_ar, author,
  version, language, license, source, generated_at)

## 2. Verification matrix (emulator-5554, Android 14, API 37)

### 2.1 Fresh install
Uninstalled, installed `app-debug.apk`, drove UI to Al-Fatiha verse 1:1 tafsir.
- DB absent before first open; copied lazily on first tafsir open (171 ms).
- `files/resources/tafsir/muyassar.db` = 2 912 256 bytes (identical to asset).
- Tafsir text rendered: "سورة الفاتحة سميت هذه السورة بالفاتحة؛ لأنه يفتتح بها القرآن العظيم…"

### 2.2 Upgrade from legacy version (translation_ar.muyassar.json present)
Simulated legacy state: created `files/translation_ar.muyassar.json`, deleted the DB.
- Log: `Bundled tafsir missing - copying from asset` → copy (161 ms).
- Log: `Legacy translation_ar.muyassar.json removed`; file gone from disk.
- Text rendered correctly for verse 1:1.

### 2.3 Deleted-DB recovery
Covered by 2.2 (DB deleted, re-copied from asset without user action).

### 2.4 Corrupted-DB recovery
Overwrote `muyassar.db` with 5000 random bytes, relaunched, opened tafsir.
- Log: `ERR-9002 Bundled tafsir corrupted - restoring from asset` (WARN, category=TAFSIR).
- File deleted and restored from asset (133 ms); text for 7:2 rendered.

### 2.5 Startup / lookup performance
- Asset copy: 133–171 ms (single occurrence; happens on first tafsir open, not app startup).
- `Tafsir open`: 0–1 ms (SQLite open read-only).
- Verse lookup: instantaneous (indexed PRIMARY KEY lookup); subsequent opens reuse the open handle.
- No work performed at app startup (lazy init verified: no TAFSIR logs until first tafsir open).

### 2.6 Verse lookup correctness (full corpus)
Pulled device DB; compared against the authoritative legacy source
(`https://api.alquran.cloud/v1/quran/ar.muyassar` — same edition the legacy
downloader fetched at runtime).
- Expected 6236, DB 6236. Missing 0, extra 0, mismatched 0 — 100% identical text.

### 2.7 On-screen behavior (UI parity)
- Same bottom sheet, same actions (استماع / تفسير / ترجمة / مشاركة), same tab row:
  التفسير الميسر (tafsir) + Traduction Française + English Translation.
- Tafsir tab: SQLite-backed text, right-aligned, verse 1:1 and 7:2 verified on screen.
- Translation tabs: unchanged legacy flow ("هذه النسخة غير محملة…" + download button).
- Tab switching both directions re-renders correctly.
- Tafsir selection persisted: `selected_tafsir_id=muyassar` in `quran_prefs.xml`.

### 2.8 Memory
- TOTAL PSS 155 978 KB with tafsir sheet open and DB loaded (includes Quran fonts,
  page data, audio catalog); Dalvik 24.9 MB, Native 20.3 MB. DB adds ~2.9 MB on disk;
  256-entry LRU text cache bounds memory. No anomaly.

### 2.9 Build & lint
- `gradlew :app:assembleDebug` — SUCCESS.
- `gradlew :app:lintDebug` — project-wide pre-existing failures (254 errors, e.g.
  `AlAdhanProvider.kt:50 NewApi`) unrelated to this phase; no baseline file exists.
  All findings in touched files are pre-existing code (SurahDetailScreen.kt:78
  `FlowOperatorInvokedInComposition`, :142 `AutoboxingStateCreation` — untouched lines).
  No new lint findings introduced.

## 3. Instrumentation

All log lines emitted through the existing debug pipeline
(category=TAFSIR / PERFORMANCE, session id, trace, code prefix):
- `Bundled tafsir missing - copying from asset`
- `Bundled tafsir copied to <path> (<bytes> bytes)`
- `Legacy translation_ar.muyassar.json removed`
- `ERR-9002 Bundled tafsir corrupted - restoring from asset`
- `Bundled tafsir asset copy in <ms> ms` (PERFORMANCE)
- `Tafsir open in <ms> ms` (PERFORMANCE)

Error codes added (9xxx block, stable numbering):
- 9001 `TAFSIR_DB_UNAVAILABLE`, 9002 `TAFSIR_DB_CORRUPTED`,
  9003 `TAFSIR_SCHEMA_UNSUPPORTED`, 9004 `TAFSIR_READ_FAILED`

## 4. Constraint compliance

- No Room / no new dependencies (android.database.sqlite + existing coroutines/logging).
- No business logic changes; no UI redesign (parity verified above).
- No download code, no catalog, no manager screen, no runtime network requests.
- No placeholders, no TODO comments.
- Architecture doc untouched (still frozen); migration section unchanged.
- Runtime behavior matches the previous implementation exactly (same data source, same UI).

## 5. Known notes

- `TafsirSelectionStore.selectTafsir` currently always persists the bundled id
  (single installed tafsir in Phase 1); Phase 2 will use the same store for the
  user-selected downloadable tafsir.
- The legacy file cleanup (`translation_ar.muyassar.json`) runs only during the
  self-heal path (first successful copy/repair). Devices upgrading with that file
  present are handled in 2.2.
- `TafsirReader` retries a read once after closing the DB handle on
  `SQLiteException` (TAFSIR_READ_FAILED), matching the reader contract §3.2.6.
