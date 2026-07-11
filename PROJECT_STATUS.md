# TrailLog – Project Status

**Version:** 1.0  
**Last Updated:** July 10, 2026  
**Status:** Offline-first with WorkManager + Firestore persistence  
**Repository:** https://github.com/friendstflo/TrailLog  

---

## 1. Project Overview

**TrailLog** is a practical, offline-first Android application built for **The Mountaineers – Everett Lookout Trail Maintenance Committee**.

### Purpose
Allow trail crews to:
- Drop pins on wilderness trails (logs, brushing, treadwork)
- Attach photos
- Record severity, quantity (inches for logs / feet for linear work), and landowner/team
- Mark work as completed
- Export reports for landowners (US Forest Service / Snohomish County Parks)

### Key Design Goals
- Fully usable offline in the forest
- Simple, field-friendly UI
- Multi-crew collaboration via Firebase
- Landowner-specific filtering and reporting

### Attribution
Credit and maintenance of the app should be attributed to:  
[The Mountaineers Everett Lookout Trail Maintenance Committee](https://www.mountaineers.org/locations-lodges/everett-branch/committees/everett-lookout-trail-maintenance-committee)

---

## 2. Current Status (July 10, 2026)

| Area                        | Status          | Notes |
|----------------------------|-----------------|-------|
| Core Map + Pin Drop        | ✅ Stable       | Long-press, Pin Here, GPS centering |
| Photo Capture              | ✅ Stable       | Camera + local storage + Firebase Storage |
| Reports List               | ✅ Stable       | Thumbnails, severity colors, landowner filter |
| Stats Dashboard            | ✅ Stable       | Filtered totals (logs, brushing, treadwork) |
| Landowner Filtering        | ✅ Stable       | Persistent across tabs and restarts |
| CSV Export                 | ✅ Working      | Filtered by current landowner |
| Authentication             | ✅ Working      | Email/password + AuthStateListener |
| Firebase Sync (Write)      | ✅ Working      | Local-first → WorkManager upload |
| Firebase Sync (Read)       | ✅ Working      | Listener + pull; cache-aware merge |
| Firestore offline cache    | ✅ Enabled      | PersistentCacheSettings 100 MB |
| Offline-First Architecture | ✅ Implemented  | Room is source of truth |
| ViewModels                 | ✅ Implemented  | Map / Reports / Stats / Settings |
| Photo Deletion from Storage| ✅ Fixed        | Via pending soft-delete + sync |
| WorkManager background sync| ✅ Implemented  | Immediate on write + 15 min periodic |
| Photo compression          | ✅ Implemented  | Max edge 1600px JPEG ~78% before upload |
| Offline / pending banner   | ✅ Implemented  | MainActivity; tap to force sync when online |
| Trail presets (land mgr)   | ✅ Implemented  | Snohomish / Darrington / Gifford-Pinchot |
| Severity-colored map pins  | ✅ Implemented  | Teardrop pins (low/med/high/complete) |
| Cache this map area        | ✅ Implemented  | FAB + OSMdroid CacheManager |
| Material 3 / dynamic color | ✅ Implemented  | DynamicColors + M3 theme; Compose Stats |
| Compose Stats              | ✅ Implemented  | First Compose screen (Material 3) |

**Overall Assessment**  
Field-ready offline stack with compression, sync visibility, and land-manager map presets.

---

## 3. Architecture

### Current (post-modernization)
- **Room** = single source of truth (UI observes `TrailLogRepository.reports` fed by DAO Flow)
- **Mutations** = write Room → optimistic UI → `SyncScheduler.enqueueImmediate`
- **WorkManager** = push pending uploads/deletes + pull remote (`SyncWorker`, 15 min periodic)
- **Firestore** = disk persistence (100 MB) + snapshot listener; not UI source of truth
- **ViewModels** for Map, Reports, Stats, Settings
- **AuthStateListener** in `MainActivity` (remembered login + logout)
- Soft-delete (`isInvalidated`) until remote delete succeeds

### Still planned
- Photo compression before upload
- Multi-device conflict resolution polish
- Offline status banner

---

## 4. Completed Features

- Bottom Navigation (Map / Reports / Stats / Settings)
- Custom info window with photo thumbnail + Edit / Complete / Delete
- Severity colors (Low = yellow, Medium = orange, High = red, Complete = green)
- Log diameter in inches (crew) vs count of 1 for landowner reports
- Landowner filtering (Settings + Reports + Stats + Map)
- Persistent crew name and default landowner
- Force sync (Reports pull-to-refresh; Settings long-press last-sync text)
- CSV export with proper columns
- Adaptive launcher icons
- Offline map tiles (OSMdroid)
- Local-first writes (`isOfflineCreated`) with later push via `syncAll()`

---

## 5. Recent Changes (WorkManager + Firestore persistence)

- `TrailLogApp` — Firestore `PersistentCacheSettings` (100 MB) before any use
- `SyncScheduler` — immediate (on write) + periodic (15 min) unique work
- `SyncWorker` — auth-gated push/pull with retry
- `TrailLogRepository` — local-first mutations only enqueue sync; soft-delete; photo upload in push
- DAO: `getById`, `getPendingDeletes`, pending uploads exclude invalidated
- Manifest: `android:name=".TrailLogApp"`, `ACCESS_NETWORK_STATE`

---

## 6. Known Issues / Technical Debt

| Issue | Severity | Status | Notes |
|-------|----------|--------|-------|
| No photo compression | Medium | Future | Bandwidth savings |
| Conflict resolution polish | Low | Partial | Pending local wins over remote merge |
| No map clustering | Low | Future | Nice-to-have |
| Material 3 / Compose | Low | Future | Optional |

---

## 7. Next Steps (Prioritized)

**Immediate**
1. Device test: airplane mode pin → reconnect → WorkManager upload
2. Device test: offline pin → reconnect → appears in Firestore
3. Device test: delete pin → Storage photo removed
4. Optional: dedicated Force Sync button on Settings

**Short-term**
- Photo compression before upload
- Soft delete + multi-device conflict resolution
- Offline status banner
- Logout button polish

**Medium-term**
- Map clustering
- Material 3 / Dynamic Color
- Optional Jetpack Compose migration (Stats first)

---

## 8. Key Files Overview

**Core**
- `data/TrailLogRepository.kt` — Room SoT + Firebase sync
- `data/TrailReport.kt`
- `data/AppDatabase.kt` + `TrailReportDao.kt`

**UI**
- `ui/MainActivity.kt` — AuthStateListener
- `ui/map/MapFragment.kt` + `MapViewModel.kt`
- `ui/reports/ReportsFragment.kt` + `ReportsViewModel.kt`
- `ui/stats/StatsFragment.kt` + `StatsViewModel.kt`
- `ui/export/SettingsFragment.kt` + `SettingsViewModel.kt`
- `ui/auth/LoginFragment.kt`

**Sync**
- `sync/SyncWorker.kt`

---

## 9. How to Resume Development

**Quick Resume Prompt**

> Continue TrailLog modernization. Next: schedule WorkManager periodic sync, device-test offline create + delete photo from Storage, then photo compression.

---
