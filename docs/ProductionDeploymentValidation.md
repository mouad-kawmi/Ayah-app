# Production Hosting Migration & Phase 2 Validation Report

Date: 2026-08-03 · Device: Redmi 2311DRK48G (Android, adb `79ZHJZJBYLHIYDSC`) · Build: debug APK with `RESOURCE_BASE_URL=https://raw.githubusercontent.com/kawmimouad1-hash/ayah-resources/main/`

Scope: migrate the production tafsir catalog + 6 SQLite artifacts from the temporary LAN server (`http://192.168.1.119:8080/`) to GitHub Releases, switch the app configuration, rebuild, reinstall, and re-validate the full production delivery workflow end-to-end on real hardware (per `docs/TafsirDownloadSystem.md` §13 and Phase 2A validation matrix items 1-9).

## 1. Hosting state (final)

- Repository: `kawmimouad1-hash/ayah-resources` (public, default branch `main`)
- Catalog file (served to the app): `https://raw.githubusercontent.com/kawmimouad1-hash/ayah-resources/main/resources/catalog.json` — committed, live, `generatedAt 2026-08-03T15:30:06Z`, 6 resources
- Release: `v1.0.0` with 7 assets (`catalog.json` + 6 DBs) at `https://github.com/kawmimouad1-hash/ayah-resources/releases/download/v1.0.0/`

| Asset | Size | SHA-256 (catalog) |
|---|---|---|
| muyassar-1.0.0.db | 2,801,664 | 0b68f00b9c346694da5521543b183bf6961c6525ae246b598e80e0b57cfac804 |
| ibn_kathir-1.0.0.db | 90,611,712 | 95674a8ff654886e5813bf3e8766665b247bdf059d0bbe82b40e12ab16f9f63f |
| tabari-1.0.0.db | 61,987,840 | 7cc2322653b88f364e7e527b39c86cdc7f317048daaaa1088ec980f979585881 |
| qurtubi-1.0.0.db | 79,016,960 | 8c1df4ffca76c12b442b7b118d0b071a059c0c33bd0ee99062b44331f415db95 |
| baghawi-1.0.0.db | 39,552,000 | c408a4a9d574eb88bc1ed5d19ad4ac5786c036fe529e2b256f5eb9ea25a5a14f |
| fath_al_qadir-1.0.0.db | 180,793,344 | b889d72f240f06357bae9b37edf66df51a418562027bd6d343d5ad97fe4e59ab |
| catalog.json | 8,074 | — |

## 2. Configuration change

`app/build.gradle.kts` (debug + release):

```diff
- debug:   buildConfigField("String", "RESOURCE_BASE_URL", "\"http://192.168.1.119:8080/\"")
- release: (empty -> defaultConfig "")
+ debug:   buildConfigField("String", "RESOURCE_BASE_URL", "\"https://raw.githubusercontent.com/kawmimouad1-hash/ayah-resources/main/\"")
+ release: buildConfigField("String", "RESOURCE_BASE_URL", "\"https://raw.githubusercontent.com/kawmimouad1-hash/ayah-resources/main/\"")
```

No runtime code was changed (download manager, catalog schema, SHA verification, resume logic untouched — frozen architecture respected). Catalog endpoint uses the raw content URL; DB assets use the GitHub Releases CDN (Range-capable, verified 206).

## 3. Release verification (`verify_assets.ps1`, fixed)

The verifier's Range request was broken (PowerShell/.NET rejects the `Range` header via the request-header dictionary). Fixed by using `HttpWebRequest.AddRange(0,99)`.

| Check | Result |
|---|---|
| raw catalog endpoint: status 200, 6 entries, `generatedAt` == local catalog | PASS |
| release asset `catalog.json`: status 200 | PASS |
| GET download + `Content-Length` == `downloadSizeBytes` (all 6 DBs) | PASS |
| SHA-256 of downloaded file == catalog `sha256` (all 6 DBs) | PASS |
| `Range: bytes=0-99` → HTTP 206, length 100 (all 6 DBs) | PASS |

## 4. On-device validation (all driven through the real UI via adb, verified by device log + on-disk inspection)

| # | Item | Result | Evidence |
|---|---|---|---|
| 1 | Fresh catalog fetch from GitHub | PASS | `Catalog downloaded (6 entries, generated 2026-08-03T15:30:06Z)`; on-disk `files/resources/catalog-cache.json` == GitHub catalog (downloadUrl = `github.com/kawmimouad1-hash/.../v1.0.0/`) |
| 2 | Explicit refresh (Refresh button) | PASS | second `Catalog downloaded (6 entries, generated 2026-08-03T15:30:06Z)` at 18:29:45 |
| 3 | All 6 tafsirs listed with metadata | PASS | UI dumps: fath_al_qadir, baghawi, muyassar (المحدد, v1.0.0, 2.7 MB), qurtubi, tabari, ibn_kathir — sizes match catalog |
| 4 | Full download from GitHub Release CDN | PASS | `Download started: tafsir:qurtubi v1.0.0 from https://github.com/.../qurtubi-1.0.0.db` (18:31:44) → `Installed tafsir:qurtubi (75 MB, schema 1)` (18:31:50, ~6 s) |
| 5 | SHA-256 + install verification | PASS | on-device `sha256sum files/resources/tafsir/qurtubi.db` = `8c1df4ffca76c12b442b7b118d0b071a059c0c33bd0ee99062b44331f415db95` == catalog; `index.json` entry (version 1.0.0, sizeBytes 79016960, same sha) |
| 6 | On-disk DB integrity | PASS | pulled DB: user_version=1, tables `tafsir`/`meta`, 6236 rows, 2 expected upstream-empty verses (34:54, 59:19) |
| 7 | Selection | PASS | `Tafsir selected: qurtubi` (18:33:03); UI chip المحدد moved to qurtubi card; button set becomes حذف |
| 8 | Reader | PASS | `TafsirReader.openForReading:262 \| Tafsir open (qurtubi) in 0 ms`; tafsir sheet shows tabs ميسر / قرطبي |
| 9 | Delete installed tafsir | PASS | `Resource deleted: tafsir:qurtubi` (18:40:36); DB file removed; `index.json` back to `{}`; qurtubi card reverts to تحميل |
| 10 | Selection fallback to Muyassar | PASS | `Selected tafsir 'qurtubi' not installed — falling back to muyassar`; persisted `selected_tafsir_id = muyassar` (quran_prefs.xml) |
| 11 | No crashes | PASS | no FATAL/ANR for `com.example.quranapp` across the session |

Note: the phone was found with an empty installation index (Phase 2A test installs had been removed); the smoke test therefore exercised a genuine fresh download from GitHub Releases (no cached local artifacts involved).

## 5. Cleanup performed

- Temporary LAN server `tools/tafsir/serve_resources.py` (both python processes) stopped — no local serving anymore.
- Device temp file `/sdcard/qurtubi_pull.db` removed; local pulled DB removed.
- Temporary credential files from earlier debugging removed.

## 6. Remaining steps

1. Release build + Play Store preparation (permissions attribution, privacy policy) — unchanged from Phase 2B.
2. If artifacts are ever rebuilt, regenerate the release assets with the same build run (`build_production_tafsir_db.py`) and publish as a NEW release tag — do not overwrite `v1.0.0` (mixing builds across runs invalidates SHAs).
3. Optional: `adb shell cmd package compile` / AAB splits remain standard release tasks.
