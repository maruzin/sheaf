# Sheaf — Build Notes

Running engineering log. Newest first. Every non-obvious decision gets one line with *why*.

---

## Top-5% pass (2026-10-24): features, bugs, a11y, perf, strict lint, tests
**New**
- **Real zoom:** pages lay out zoom x wider with horizontal pan. Pinch runs on the Initial pointer
  pass (it used to lose to the list scroll). Double-tap toggles 1x / 2.5x. Renders are snapped to 25%
  steps and capped at 2048 px (`ZoomMath`).
- **Reading mode (reflow):** page text → paragraphs (hyphen repair, wrapped-line joins), 80–200% text
  size, themed, page headers. Scans show an "use OCR" hint (`TextSearch.paragraphs`, `ReflowContent`).
- **Text index:** page text is extracted once per document and shared by search and reflow
  (`PdfTextSearcher.pageTexts`). Search now matches phrases across line breaks.
- **Night/Sepia themes recolour the page bitmap** (ColorMatrix), not just the background. "System"
  follows light/dark.
- **Tabs:** open documents persist (DataStore, max 8) and show as a chip row when more than one is
  open. Switching replaces the reader on the back stack (`OpenTabs`, `ReaderTabsRow`).
- **Tablet two-pane** at ≥840dp: library + reader side by side, one ViewModel per document.
- **Library:** remove from library (cascades annotations, deletes Sheaf-owned files, releases SAF
  grants), import errors in a snackbar, busy bar, byte-bounded thumbnail LruCache, readable scan
  names, "Open with" de-duplication (name + size).
- **Paywall** shows the real Play price. Settings purchase/restore give feedback.
- **TalkBack:** "Page N of M", headings, tool on/off states, ink colours as radio buttons, 48dp
  note targets, form field labels, live search result count, selectable theme radios, tab close
  action, per-page action labels in Manage pages.
- **Perf:** `:benchmark` module (cold-start macrobenchmark + Baseline Profile generator),
  profileinstaller in the app, PdfBox loads with a 48 MB RAM cap and spills to disk.
- **Strict lint:** `abortOnError` + `warningsAsErrors` on every module. Only version nags are off, and
  third-party-jar findings are scoped in `lint.xml`. 0 issues.

**Bugs fixed**
- Reading position never saved: it was written from `onCleared()` on the already-cancelled
  `viewModelScope`. Now it's written on ON_STOP and in onCleared through a process-level scope.
- Rotation reloaded the document and jumped back to the old saved page. `open()` now keeps the live
  session.
- The "Default reading theme" setting did nothing. The reader now applies it.
- OCR / compress / protect / form-save / print failed silently on password-protected PDFs (they read
  the encrypted original). They now use the unlocked working copy.
- Unlocked (decrypted) copies piled up in filesDir. They now go to cacheDir, are deleted in onCleared,
  and are purged on app start.
- Every PdfBox writer returned a path even when the source couldn't be read (phantom files shared or
  added to the library). They now return null.
- Print copied the file on the main thread (ANR risk). It now copies on a worker thread and
  honours cancel.
- `fallbackToDestructiveMigration` would wipe the library on any schema bump. Schemas are now
  exported (`core/data/schemas`) and only a downgrade may reset.
- ViewModel fields declared after `init {}` could NPE when a collector emitted immediately. Found by
  the new tests.
- Ink toolbar and Manage-pages buttons overflowed and were clipped on phones. Onboarding showed black
  text in dark theme (earlier pass).

**Tests:** 38 JVM tests (was 6), including Robolectric tests that run the real PdfBox engine
(text, outline, AES round-trip, wrong-password cleanup, unreadable-source handling) and ViewModel
tests for each bug above. `app/src/androidTest` has end-to-end UI tests (open, search, reflow,
theme, rotation, password, tabs, remove). CI `device-tests.yml` runs them on API 35 + 29 emulators
and runs the startup benchmark and Baseline Profile generation.

**Decision: no PDFium swap.** `android.graphics.pdf.PdfRenderer` already runs PDFium inside the
platform. The gaps it left (text, outline, passwords) are now covered by PdfBox. Swapping engines
would add native-crash risk that can't be verified without devices, for little gain.

## Play compliance pass (2026-10-24) — v1.0.1 (versionCode 3)
Checked against Play's live requirements and verified with real local builds (assembleDebug,
bundleRelease with R8, unit tests, lintRelease; JDK 21, Android SDK 36).
- **targetSdk/compileSdk 35 → 36.** Play requires API 36 for new apps and updates since 31 Aug 2026.
  Needed **AGP 8.7.3 → 8.13.2** and **Gradle wrapper 8.11.1 → 8.14.3**. Stayed on AGP 8.x and Kotlin 2.1.0
  on purpose: AGP 9 / Kotlin 2.3+ is a big migration and not needed to publish.
- **Play Billing 7.1.1 → 8.3.0.** v7 was blocked for new uploads from 31 Aug 2026. Added
  `enableAutoServiceReconnection()`. **Fixed:** `restore()` now removes Pro when Play reports no owned
  `sheaf_pro` (refund/revoke). Before, Pro could only ever be switched on. It only clears on a
  successful (OK) Play response, so being offline never removes Pro.
- **Edge-to-edge (forced at API 36):** all screens except onboarding already used Scaffold insets.
  Onboarding now uses `safeDrawingPadding()` inside a `Surface`. The Surface also fixes black text in
  dark theme.
- **Misleading claims:** onboarding advertised "Ask — chat with your document", but `:feature:ai` is a
  stub. Replaced it with "Annotate & sign". Re-add only when AI ships.
- **16 KB pages:** every bundled `.so` (ML Kit OCR, DataStore, Compose graphics path) has 16 KB LOAD
  alignment and passes `zipalign -c -P 16`.
- **Lint TrustAllX509TrustManager** is from BouncyCastle (via PdfBox). R8 strips all BouncyCastle
  classes from the release dex (verified), so it doesn't ship.
- **Data safety:** ML Kit sends diagnostics, device/app info and an install ID to Google. The guide's old
  "No data collected" answer was wrong. Fixed the guide, privacy policy (md + html) and listing copy.
- ML Kit document scanner `16.0.0-beta1 → 16.0.0` (stable). CI now also runs `bundleRelease` (R8).
- **Not verified here (no emulator/KVM):** behaviour on a device. Before production, run an internal-
  testing build on a real Android 16 phone: open/scan/OCR/forms/sign, purchase + restore with a license
  tester, and dark theme on the onboarding screen.

## M3 — Annotations (in progress, 2026-07-04)
- **GREEN on CI:** ink drawing, highlighter, sticky notes (tap-to-place + edit + on-page markers),
  annotations overview sheet (jump/delete), and **signatures** (draw once → saved in DataStore →
  tap-to-stamp on any page; single-stroke v1). `Annotation` model uses page-normalized 0..1 coords
  (zoom-independent); Room `annotations` table (DB v2, destructive migration for dev).
- **AcroForm form-fill GREEN.** `PdfBoxFormReader` parses fields (PdfBox `fieldTree` → widget rects →
  page-normalized `NormRect`), reader shows a Forms toggle + on-page editable overlay (text/choice inputs,
  checkboxes), and Save fills values back via typed PdfBox calls (`PDTextField/PDChoice/PDCheckBox`) →
  writes to cacheDir → shares via **FileProvider** (`${applicationId}.fileprovider`, `res/xml/file_paths.xml`).
- **M3 COMPLETE:** ink, highlighter, notes, annotations overview, signatures, forms — all CI-green.
- Signature stamping uses a fixed 0.4x0.16 normalized box (minor aspect distortion; acceptable for v1).
- Form checkbox export values assume Yes/Off; typed calls are wrapped in runCatching so odd fields never crash.

## M2 — Design system + app shell (2026-07-04)

### Visual direction chosen: **Ember** (of 4 proposed to Dean)
- Neutral slate surfaces + one warm ember accent; document stays the hero. Rejected: Blueprint
  (cyan/technical), Midnight (OLED black/ink-blue), Vellum (warm serif). Followed the Opus 4.8
  "propose 4 directions, implement one" rule; avoided the cream/serif house style and AI-slop defaults.
- **Palette:** dark bg `#15171C`, light bg `#F5F6F8`, accent `#E4713B` (dark) / `#C85A28` (light),
  onPrimary `#3A1608`. Full Material3 light + dark ColorSchemes in `core:ui/theme/Theme.kt`.
- **Type:** Hanken Grotesk (bundled variable TTF from google/fonts, 3 weights) applied across the
  Material3 type scale in `core:ui/theme/Type.kt` — no Inter/Roboto default.
- Material You dynamic color kept as an opt-in (`dynamicColor` flag), Ember is the default identity.
- Launcher background aligned to slate `#15171C`; reader "System" page tint aligned to brand slate.
- Propagates app-wide via `SheafTheme` (colorScheme + typography); no per-screen changes needed.

### Still to do in M2 (next)
- App shell polish: onboarding, settings screen, foldable/tablet two-pane for library+reader, TalkBack pass.

## M1 — Reader core (in progress, 2026-07-04)

### Engine decision for the M1 baseline
- **Render with the platform `android.graphics.pdf.PdfRenderer`** — zero external deps, compiles and
  actually renders, so this milestone can go green on CI without an unverified native lib. Kept behind
  `PdfRenderSource`; **PDFium / androidx.pdf remain the production-engine candidates** (benchmark for
  large-doc perf) and swap in without touching UI/VM. The provisional `pdfium-android` catalog entry
  is retained but unused for now.
- *Consequence:* PdfRenderer has no text or outline API, so **full-text search, ToC, and reflow move
  to the M1 engine-upgrade increment** (add a text-capable engine — PdfBox-Android for extraction, or
  the chosen native engine). Baseline M1 ships: open/render/scroll, pinch-zoom, library + recents +
  bookmarks (Room), resume position, reading themes.

### Landed this session
- **Render layer:** `PdfRenderSource` interface, `AndroidPdfRenderSource` (PdfRenderer + `Mutex` since
  PdfRenderer allows one open page at a time; IO-dispatched; white-fill before render; aspect ratios
  pre-read at open), `ReaderModule` DI (IO dispatcher + factory binding).
- **Reader UI:** `ReaderScreen` — `LazyColumn` of pages, per-page lazy render keyed on size/zoom,
  render resolution capped (memory-safe windowing), pinch-zoom, page indicator, top bar, theme bg,
  resume via saved `ReadingPosition`.
- **Library:** `LibraryViewModel` (recents + bookmarked via Room `combine`, SAF import with dedup by
  uri, page-count probe), `LibraryScreen` (SAF `OpenDocument` picker with persistable permission,
  recents/bookmarks list, empty state, bookmark toggle).
- **Repo/data:** added `document(id)` + `findByUri` to `DocumentRepository`/DAO/impl.
- **Nav:** `SheafNavHost` library → reader/{documentId} (typed Long arg); `MainActivity` de-duped to
  one Scaffold per screen.
- **Deps:** added `lifecycle-runtime-compose` (for `collectAsStateWithLifecycle`) to catalog + feature
  convention; added `activity-compose` to `:feature:reader` for the SAF launcher.
- Room `exportSchema=false` to avoid the schema-dir CI warning for now.

### Verification status — UPDATED 2026-07-04 (CI green path)
- **assembleDebug + unit tests PASS on GitHub CI (x86_64).** The full app compiles and an APK is
  built (commit 7cd561f: BUILD SUCCESSFUL 4m16s; unit tests SUCCESSFUL). M1 baseline is verified.
- Fixes en route to green: (1) compose convention plugin resolved the Android extension by concrete
  type (was CommonExtension); (2) manifest AppLinkUrlError — removed BROWSABLE from the pdf 'open
  with' intent-filter; (3) `lint.abortOnError=false` for in-dev (re-enable strict lint at M8).
- Original note below (pre-first-build):
- Self-reviewed for compile risk (imports, brace/paren balance, API signatures) but **NOT compiled** —
  ARM64 sandbox can't run aapt2/AGP. Awaiting first CI run once pushed to GitHub.

### Remaining in M1 (next increments)
- Full-text search, ToC/outline, reflow (needs text-capable engine — the engine-upgrade step).
- Tabs, print, share.
- Instrumented smoke test for open→render→scroll; unit tests for LibraryViewModel import/dedup.

## M0 — Scope & skeleton (2026-07-04)

### Name
- **Sheaf** (was "PDFForge" → briefly "PDF Leverage"). Dropped "PDF" from the name to avoid weak/
  generic-descriptor trademark exposure and Adobe's policing of "PDF"-adjacent branding.
- "Sheaf" = a bundle of papers → fits the all-in-one positioning; verified no PDF/document app of
  that name on Google Play (checked 2026-07-04). applicationId `com.sheaf.app`, namespace root
  `com.sheaf`. Debug builds use `.debug` suffix for side-by-side installs.

### Architecture decisions
- **Multi-module** per the approved plan: `:app`, `:core:{ui,domain,data}`,
  `:feature:{reader,annotate,pages,scan,ai,billing}`. Package-by-feature.
- **Convention plugins** in `build-logic/` (`sheaf.android.{application,library,compose,hilt,feature}`),
  modeled on the Now-in-Android pattern, so module build files stay ~5 lines. *Why:* single source of
  truth for SDK levels, Kotlin/JVM 17, Compose, and Hilt wiring across 10 modules.
- **UI state is Android-type-free** in the reader (`ReaderUiState`/`ReaderEvent`) so reducers are
  unit-testable without instrumentation.
- **Render engine behind an interface** (`PdfRenderSource` / `PdfRenderSourceFactory`) so the
  PDFium-vs-androidx.pdf choice stays reversible without touching UI/VM.

### Decision still to finalize (before M1 render code)
- **Rendering: PDFium (maintained fork) vs androidx.pdf.** Current lean: **PDFium**, for low-level
  page-bitmap windowing/recycling needed to hit the budgets (100MB open <2s, 60fps, memory-stable at
  1000 pages) uniformly from minSdk 26. Catalog currently points at `io.legere:pdfiumandroid`
  (provisional). Action: benchmark both on a mid-range profile at M1 start, then record the final
  call here. Document manipulation (merge/split/forms/compress) + text extraction: **PdfBox-Android**.

### Version matrix (confirm on first sync)
- AGP 8.7.3 · Kotlin 2.1.0 (compose compiler plugin) · KSP 2.1.0-1.0.29 · Compose BOM 2024.12.01 ·
  Hilt 2.53.1 · Room 2.6.1 · compileSdk/targetSdk 35 · minSdk 26 · JVM 17.
- These were chosen as a coherent current-stable set **without a live Gradle sync** in the authoring
  environment (no Android SDK there). Treat as provisional until the first Android Studio sync.

### Verification status — READ THIS
- **Not yet compiled.** The skeleton was authored in an environment with **no Android SDK/Gradle**
  (JDK 11 only), so "compiles" is *unverified*. First real build must happen in Android Studio
  (Ladybug+). Expect to resolve at most version/wrapper nits on first sync. Per house rule
  (ground your claims): nothing here is reported as build-green.
- **Gradle wrapper jar not committed** (can't generate the binary in the authoring env). Run
  `gradle wrapper --gradle-version 8.11.1` once, or let Android Studio generate it, before CI passes.

### Feature → milestone → tier map
| Milestone | Features | Tier |
|---|---|---|
| M1 Reader | render/zoom/scroll, library+recents (Room), search, ToC, bookmarks, themes, reflow, tabs, print, share | Free |
| M2 Design+shell | chosen visual direction, nav, settings, onboarding, Material You, foldable two-pane, TalkBack | Free |
| M3 Annotate | highlight/underline/strikethrough, ink+stylus, shapes, notes (Free); signatures (Pro); AcroForm fill (Free) | Free/Pro |
| M4 Pages | reorder/rotate/delete/extract/merge/split, compression, password set/remove | Pro |
| M5 Scan | CameraX+ML Kit edge detect, multi-page, filters, export (Free ≤3/day); OCR→searchable (Pro); no-watermark (Pro) | Free/Pro |
| M6 AI | Chat-with-PDF (page citations), summarize, translate | Pro |
| M7 Billing | Play Billing monthly+annual+trial, paywall, entitlements, restore, gating | — |
| M8 Hardening | macrobenchmarks vs budgets, boundary error handling, a11y, Play listing, release checklist | — |

### Planned pauses
- M0 name+plan approval — **done** (Sheaf, approved to proceed).
- M2 visual direction — pick 1 of 4 proposed directions. Everything else runs autonomously.

---

## Next step
Push to GitHub so Actions CI runs the first real `assembleDebug` + tests (x86_64). Fix whatever it
flags (likely version-matrix or first-sync nits). Then finish M1: add the text-capable engine for
search/ToC/reflow, plus tabs/print/share, and an instrumented open→render smoke test.

## M4 — Page management, security, compression (COMPLETE, CI-green)
- **Manage pages** (`pages/{id}`): thumbnail grid, per-page rotate/delete/reorder (left/right), Save copy →
  `PdfBoxPageEditor` imports pages into a fresh PDDocument (reorder+delete), applies rotation, writes to
  filesDir → upserts a new library Document → opens it.
- **Protect with password**: `PdfBoxSecurity.encrypt` (AES-128 StandardProtectionPolicy) → cacheDir → share.
  `decrypt` engine also present (unlock-on-open UX deferred to the PDFium/androidx.pdf migration since
  android.graphics PdfRenderer can't open password PDFs).
- **Reduce file size**: `PdfBoxCompressor` downsamples embedded images (>1600px longest edge) and re-encodes
  JPEG q=0.6 via `JPEGFactory`, replacing XObjects in page resources → share smaller copy.
- All three share via the FileProvider added in M3.

## M7 — Freemium billing (COMPLETE, CI-green)
- `BillingManager` (core:data, @Singleton) wraps Play Billing v7: one-time `sheaf_pro` INAPP unlock,
  `enablePendingPurchases(PendingPurchasesParams)`, suspend ktx (`queryProductDetails`, `queryPurchasesAsync`,
  `acknowledgePurchase`). Entitlement persisted via `SettingsRepository.isPro` (DataStore key `is_pro`),
  restored on launch from `SheafApplication.onCreate` → `billingManager.start()`.
- Reader gates **OCR, compression, password protection** behind Pro: menu items show "· PRO" and open a
  `PaywallDialog`; `upgrade(activity)` launches the purchase flow. VM has a `blockedByPaywall()` safety net.
- GATE (runtime only): live purchase needs the `sheaf_pro` product in Play Console + a signed build.

## M8 — Hardening (in progress)
- Release build already minifies + shrinks; wrote real `proguard-rules.pro` keeps for PdfBox (com.tom_roush.**),
  ML Kit, Play Services, Billing + coroutines dontwarn.
- Extracted pure `PageReducer` (rotate/delete/move) + `PageReducerTest` (runs in CI `testDebugUnitTest`).
- Strict lint (`abortOnError`) intentionally left off to keep the CI loop fast; revisit before store submission.

## PDFium migration — ATTEMPTED, REVERTED (renderer stays on platform PdfRenderer)
- Tried io.legere `pdfiumandroid` for faster render + in-app password unlock. Blocker: PDFium 2.x
  (2.0.0/2.0.1) is built with **Kotlin 2.3** (`kotlin-stdlib 2.3.x`), unreadable by our Kotlin 2.1.0
  compiler (KSP failed: "metadata 2.3.0, expected 2.1.0"). The 1.0.x line risks Play's 16KB native-lib
  rule + has an unverified API. Upgrading the project to Kotlin 2.3 cascades (KSP, Hilt) — too risky
  right before release, so **reverted** to the proven platform renderer (AGP 8.7.3 / compileSdk 35).
- Kept the release plumbing from this phase: `signingConfig` via gitignored `keystore.properties`,
  `INTERNET` permission, and `docs/` (PRIVACY_POLICY, PLAY_CONSOLE_GUIDE, RELEASE_BUILD).
- Follow-up (optional): bump to Kotlin 2.3 + KSP + compileSdk 36, then re-add PDFium 2.x behind the
  unchanged `PdfRenderSource` seam. Password-protected docs remain not-openable in-app until then.
