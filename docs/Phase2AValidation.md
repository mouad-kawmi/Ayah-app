# Phase 2A — Production Tafsir Delivery Validation Report

Date: 2026-08-03 · Device: Redmi 2311DRK48G (Android, adb `79ZHJZJBYLHIYDSC`) · Build: debug APK with `RESOURCE_BASE_URL=http://192.168.1.119:8080/`

Scope: validate the production tafsir catalog delivery (per `TafsirDownloadSystem.md` §13) end-to-end on real hardware. All behavior was driven through the real UI via adb (no instrumentation), verified by device log files and on-device SQLite inspection.

## Summary

Every item in the validation matrix passed. No crashes (logcat `FATAL EXCEPTION` / `ANR` / process death for `com.example.quranapp` across the full session: 0 occurrences). All flows exercised at least once on-device.

## Validation Matrix

| # | Item | Result | Evidence |
|---|------|--------|----------|
| 1 | Catalog download over LAN HTTP | PASS | `Catalog downloaded (6 entries, generated 2026-08-03T14:53:36Z)`; cache `files/resources/catalog-cache.json` matches server |
| 2 | Manager lists all 6 catalog tafsirs | PASS | UI dump: muyassar, fath_al_qadir, baghawi, tabari, qurtubi, ibn_kathir with metadata + sizes |
| 3 | Download → SHA-256 verify → install | PASS | baghawi v1.0.0 (37 MB): `Installed tafsir:baghawi (37 MB, schema 1)`; on-disk sha `7c3183e2…e54f1` == catalog |
| 4 | qurtubi install (75 MB) | PASS | on-disk sha `65f1bb22…9503` == catalog |
| 5 | Cancel mid-download | PASS | `ERR-10003 Cancel requested` → `paused at 7217408 bytes, resumable`; `.part` + `.part.meta` preserved |
| 6 | Resume from partial | PASS | re-download: `Resuming tafsir:qurtubi from byte 7217408` → full install, 4 s |
| 7 | Clean failure path (server unreachable) | PASS | `ERR-10001 Could not connect to the download server` → clean paused/error state, no crash |
| 8 | Delete installed tafsir | PASS | `Resource deleted: tafsir:baghawi`; DB file removed; `index.json` updated; card reverts to تحميل |
| 9 | Selection fallback after delete | PASS | `Selected tafsir 'baghawi' not installed — falling back to muyassar`; UI moves المحدد to muyassar |
| 10 | Update flow (1.0.1 → 1.0.2) | PASS | card shows `الإصدار 1.0.1 · تحديث متوفر` + تحديث button; install of v1.0.2; `index.json` → v1.0.2, sha `975125247eb6…` == catalog |
| 11 | No update when installed version ≥ catalog | PASS | baghawi 1.0.2 installed vs 1.0.0 catalog → no تحديث; qurtubi 1.0.0 == 1.0.0 (rebuilt sha) → no تحديث |
| 12 | Selection persistence across process death | PASS | `am force-stop` + relaunch → baghawi still المحدد, `index.json` intact |
| 13 | Offline fallback + cache persistence | PASS | server stopped + force-stop + relaunch: `Catalog cache hit` → `ERR-10001 Catalog refresh failed`; banner `لا يوجد اتصال بالإنترنت — تُعرض النسخ المحفوظة فقط`; all 6 entries listed from disk cache |
| 14 | Recovery when server returns | PASS | after server restart: `Catalog downloaded (6 entries, generated 2026-08-03T15:30:06Z)`, banner cleared |
| 15 | Reader opens downloaded tafsir | PASS | verse → تفسير → sheet → baghawi tab: `Tafsir open (baghawi) in 0 ms`; content TextView present, `selected=true` on baghawi tab |
| 16 | On-disk DB integrity | PASS | baghawi 6236 rows / 0 empty; qurtubi 6236 rows (2 upstream-empty verses 34:54, 59:19 as expected); schema `tafsir`/`meta`, user_version=1 |
| 17 | No FATAL/ANR/dies | PASS | full-session logcat scan: 0 matches |

## Environment Notes

- Test server: `python tools/tafsir/serve_resources.py --site tools/tafsir/production/site` on `0.0.0.0:8080` (Range support). NOTE: the default (`tools/tafsir/site`) is the deprecated fixture — always pass `--site`.
- Version bump test artifacts (baghawi 1.0.1 / 1.0.2 DBs) built, exercised, then removed; `build_production_tafsir_db.py` restored to baghawi **1.0.0**.
- Final rebuild: full 6-entry production build, `generatedAt 2026-08-03T15:30:06Z`. DBs embed `generated_at` in `meta` (mandated provenance), so every rebuild yields new SHAs; catalog and artifacts are generated in the same run and are internally consistent. Deployments serve a fixed built set — do not mix artifacts across builds.
- Phone currently holds: baghawi 1.0.2 (test build, selected), qurtubi 1.0.0 (original build). Against the restored 1.0.0 catalog no updates are offered (version-based, no downgrade); delete + re-download yields the current artifacts.

## Actions Before Phase 2B Deployment

1. Set the real hosting base URL (replace the debug `http://192.168.1.119:8080/` in `app/build.gradle.kts:33`) and rebuild the release catalog with `--base-url` set accordingly.
2. Re-run item 3/4/13 smoke tests against the release URL before sign-off.

---

## Phase 2B — First Deliverables (2026-08-03)

### Sources & Licenses screen (§13.4) — implemented
- New route `Screen.SourcesLicenses` (`sources_licenses`), entry point `Settings → عام → المصادر والتراخيص`.
- `SourcesLicensesViewModel` is read-only: builds entries from the **embedded catalog + disk cache** (`ResourceCatalogRepository.getCatalog()`) and the **installation index** (`ResourceIndexStore.allEntries()`). It performs **no network work** — the screen is offline by construction.
- Per tafsir (bundled and downloadable): name (+ `nameLatin`), author, source, edition, publisher, license, website, version (index version when installed, else catalog), plus copyright notice; status chip `مثبت مع التطبيق / مثبت / غير مثبت`.
- Intro line restating the §13 policy (single preparation from authorized sources; no third-party runtime APIs).
- Validated on-device online and **offline** (server stopped — all 6 cards still render from cache).

### Tafsir Manager UI polish
- Initial **loading state** (`loading` flag in `TafsirManagerViewModel`) — centered spinner while the catalog/index load.
- Defensive **empty state**.
- **Update badge**: `تحديث متوفر` chip on the card header (gold) driven by the existing `updateAvailable` flag (replaces the inline "· تحديث متوفر" text suffix). Verified on-device both ways: badge + تحديث button shown against a temporary catalog 1.0.3, cleared when the catalog was restored to 1.0.0.
- No changes to the download/schema/catalog layer (frozen architecture untouched).

### Still open (Phase 2B)
- Release configuration: real hosting URL (HTTPS) + release build + smoke test of items 3/4/13 — blocked on the production URL.
- Play Store preparation (permissions attribution, privacy policy).
- Tafsir Manager: future UX refinements if needed.
