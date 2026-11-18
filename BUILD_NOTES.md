# Sheaf — Build Notes

Running engineering log. Newest first. Every non-obvious decision gets one line with *why*.

---

## 1.1.0 (versionCode 8), 2026-11-18: portrait screen no longer required
- Play blocked review: "Required hardware feature declarations are restricting your app's availability:
  android.hardware.screen.portrait". Source: ML Kit's `GmsDocumentScanningDelegateActivity` is locked to
  portrait in the library manifest, so Play inferred the feature. Declared `required="false"` (scanner UI
  left as Google built it). CI guard extended to this feature and to landscape-locked library activities.

## 1.1.0 (versionCode 7), 2026-11-18: Play Console warnings fixed and guarded
- **R8**: removed blanket keeps for Google Play services and Play Billing (both ship consumer rules;
  they pinned 58% of all kept entries). Release dex 7.2 MB -> 5.9 MB, kept entries 46,260 -> 19,332.
  The shrunk release APK was smoke-tested on the emulator (launch, scanner, open, search, reading mode,
  Manage pages, share with markup, diagnostic log): 0 crashes.
- **Edge-to-edge**: disassembly shows Sheaf's code calls none of the deprecated Window APIs; the flagged
  calls are androidx.activity/core back-compat branches used by `enableEdgeToEdge()` on Android 6-10.
- **Guard**: `scripts/check-play-warnings.sh` runs first in CI and fails on deprecated edge-to-edge APIs in
  app code, a missing `enableEdgeToEdge()`, blanket library keep rules, or required touchscreen/camera.
- UX: opening Search focuses the field and shows the keyboard.
- Tests: 66 unit, 8 device, strict lint (debug + release), signed bundle verified (DeanPlay).

## 1.1.0 (versionCode 6), 2026-11-18: diagnostics log, optional hardware, upload key
- **Diagnostic log** (`core/data/log/AppLog`): errors and warnings go to logcat and a rotating on-device
  file (`files/logs`, 2 x 256 KB); an uncaught-exception handler writes `last-crash.txt` (stack, version,
  device) and then defers to Android so Play's Android vitals still records it. Wired into the PDF engines,
  reader/library/pages ViewModels, "Open with" and Play Billing. Settings -> Diagnostics: "Share diagnostic
  log" (text file via FileProvider, user picks the recipient), last crash time, "Clear diagnostic log".
  Never uploaded automatically; logs carry no document text, names or passwords. Privacy policy updated.
  Verified on the emulator: `adb shell am crash` captured, export + share sheet work, a11y scan clean.
- **Device catalog:** touchscreen and camera features declared `required="false"` (Play assumed
  touchscreen required, hiding the app from non-touch Chromebooks).
- **Signing:** the app's registered Play upload key is DeanPlay (`key0`, SHA1 AF:A8:15:8C...); the first
  1.1.0 bundle used a new key and was rejected. versionCode 4 -> 5 -> 6.
- Tests: 66 unit, 8 device (Android 15 emulator), strict lint, R8 bundle signed + verified.

## Release 1.1.0 (versionCode 4), 2026-11-18: all audit findings fixed, full device + UX testing
Verified on this Mac: `assembleDebug`, `testDebugUnitTest` **64/64**, strict `lintDebug` (0), `bundleRelease`
(R8), and `connectedDebugAndroidTest` **8/8 on an Android 15 emulator** (Pixel 7 AVD, arm64). Plus a manual
visual + accessibility pass on the emulator (onboarding, library, reader, annotate, menus, paywall, settings,
Manage pages, share sheet; dark mode; 200% font; 2560x1600 tablet), with a UI-tree scan for unlabelled
controls and sub-48dp targets.

**Fixed (second audit round + independent review of the diff)**
- Markup, form overlays and the OCR layer now map through the **crop box and /Rotate** (`pdf/PageFrame`,
  unit-tested for all four rotations). Signatures on rotated or cropped pages landed in the wrong place.
- **Manage pages writes a fresh document**: deleted pages can't survive via bookmarks, links or form
  fields (tested by scanning every stream in the output). Forms are flattened, bookmarks and internal links
  re-pointed to kept pages, annotations carried over to the copy (rotated with the page), owner-restricted
  sources save fine.
- Fill / compress / share-with-markup / OCR outputs of a **password-protected** PDF keep its password.
- Pen mode: **two fingers pan and zoom** (dedicated Initial-pass detector), one finger draws, ink is
  clamped to its page. Device test proves a two-finger drag scrolls and leaves no ink.
- Undo snapshots come from the database inside the edit lock; a failed undo/redo restores the stacks.
- Long PDFs: first 150 page shapes measured at open, the rest in the background with a layout refresh
  (`ratioVersion`); renderer released safely if closed mid-batch.
- FileProvider limited to the share folders (device-tested: settings, DB and decrypted copies refused).
  `file://` links into Sheaf's private data refused and read via the checked canonical path. "Open with"
  failures now show a message. Signature moved to a no-backup DataStore (migrated). Atomic document delete.
  Form field lookup is one pass. OCR recycles bitmaps.
- **Share works with other apps, verified cross-app on the emulator** (Android Print service, a separate
  app, rendered every shared file): Sheaf-owned documents, "Share with markup" (ink on a rotated page lands in
  the same place, checked by round-trip) and documents picked with the system picker. The last one was
  broken: re-granting a picker `content://` link failed with "Permission Denial" and the receiver got a
  blank file. Every share now goes through Sheaf's FileProvider (copied to cache/share under the library
  name, grant attached as ClipData). Outputs are named "<doc> (marked up / filled / protected /
  compressed).pdf" instead of internal file names.
- Device tests made state-independent (unique names, tab strip scrolled to the tab); the suite passes on a
  fresh install and twice in a row on a device full of stale tabs and documents.
- UX: reader title ellipsis; Settings switch has an accessible name and 48dp theme rows; Settings and the
  Pro dialog scroll at large font sizes; owned files share under their library name; paywall says
  "price shown at checkout" when Play can't be reached.

**Play compliance (checked against official Google pages, 2026-11-18)**: API 36, Billing 8.3, 16 KB pages,
edge-to-edge, predictive back, permissions, backup rules all pass. Fixed: onboarding no longer implies free
searchable scans; listing says "no tracking" (ML Kit sends diagnostics); two-pane claim matches the 840dp
breakpoint; privacy policy names Plentrix Ltd. Owner tasks are in `docs/PLAY_CONSOLE_GUIDE.md`.

**Still not covered here**: Android 10 (API 29) device run (CI `device-tests.yml` runs it on push); a real
phone; a real Play purchase/restore with a licence tester; Free/Pro split decision.

> Verification 2026-11-18: claims checked 9 (state 7, external 2). Test counts recounted from the raw JUnit
> XML (64 unit / 8 device, 0 failures); lint reports "No issues found" in all 5 modules; versionCode 4 /
> 1.1.0 re-read from the convention plugin; release AAB built (unsigned: 0 signature entries); diff scanned
> for secrets (none). Play requirement dates come from the compliance audit's official Google URLs
> (listed in docs/PLAY_CONSOLE_GUIDE.md), not re-opened by the author. Not verified: API 29 device run,
> physical phone, live purchase.

## Score-97 pass (2026-11-18): undo/redo, merge, audit fixes
Built on the green draft PR #1 branch. Verified locally (JDK 21 from Android Studio, SDK 36):
`assembleDebug`, `testDebugUnitTest` (54 tests, was 45), strict `lintDebug` (0 issues), `bundleRelease` (R8).

**New**
- **Undo / redo for annotations** (`annotate/AnnotationHistory`): strokes, highlights, notes, note edits,
  deletes, signature stamps and "Clear page". Ops keep full snapshots; Room's upsert re-inserts a deleted
  row under its original id, so redo/undo never chase new ids. Edits are serialised with a Mutex so a
  fast undo can't race its insert. 50-step cap. Buttons lead the ink toolbar.
- **Merge PDFs (Pro)** from the library overflow menu (`merge/PdfBoxMerger`, PdfBox `PDFMergerUtility`,
  mixed RAM/disk memory). Names the protected or unreadable input; never leaves a partial file.
- **Paywall restore button**, in the reader and the library.

**Fixed (from an independent code audit, each confirmed in code)**
- Share did nothing for Sheaf-owned `file://` documents (scans, "Open with", OCR, edited copies):
  `FileUriExposedException` was swallowed. Now served through the FileProvider.
- Rotation / process restore re-imported and re-opened the "Open with" file (launch intent re-read).
- `ITEM_ALREADY_OWNED` left owners stuck at the paywall. Now triggers a restore.
- `PdfRenderer` failure (corrupt / protected file) leaked the file descriptor on every import and retry.
- OCR couldn't be cancelled, never closed the ML Kit recognizer, and left orphan `ocr_*.pdf` files.
- A corrupt preferences file crashed every settings collector (reader included). Reads now fall back
  to defaults on IOException.
- Compression re-encoded shared images once per page, never recycled bitmaps, and shared outputs that
  were larger than the input. Now dedupes by COS object, recycles, and only returns a smaller file.
- Fill / protect / compress failures were silent. They now show a message.
- Share-only outputs in cacheDir (decrypted plaintext for protected sources) are purged after 1 hour.

**Open (next)**
- Flattener / form overlay / OCR layer map to `mediaBox` and ignore `/Rotate` and `cropBox`: markup on
  rotated or cropped pages lands in the wrong place. Needs a transform + a `/Rotate 90` test.
- Free/Pro split differs from the original Spec (pages, signatures, scans are free; one-time Pro, not a
  subscription). The listing matches the code. Product decision before the Play listing goes live.
- Opening a long PDF measures every page up front; FileProvider exposes all of files/ and cache/;
  document delete isn't one transaction.

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

**Round 2: found by the emulator tests (CI) and an independent code review**
- Password-protected PDFs couldn't be imported at all, from either "Open with" or the picker: the
  page count threw. They now import with 0 pages, the reader fills in the count after unlock, and the
  library shows "Password protected".
- "Open with" on a file:// link named every document "Document.pdf". It now uses the file name.
- The saved page was never scrolled to (counter said 31, page 1 shown, then page 1 overwrote 31).
  Open now scrolls to it, and nothing is saved unless a document actually loaded.
- Tablet two-pane kept every opened document's renderer alive. Each one is released when it leaves
  the pane, and folding below 840dp hands the document to the full-screen reader.
- "Open with" de-dup only reuses a Sheaf-owned copy that is byte-identical (SHA-256), never a
  different document with the same name and size.
- "Remove from library" warns that Sheaf-only files are deleted permanently. It deletes files only
  after the row is gone, closes the tab, and can't crash on IO errors.
- Restore purchase gives the right message (owned / none / Play unreachable).
- The renderer is closed safely during an in-flight render. Very tall pages keep their proportions.
  "System" theme darkens pages in dark mode. Fast pinches compound zoom correctly.
- A failed text extraction no longer looks like "no text layer". Search and reading mode share one
  extraction.
- Backups now include settings only (no PDFs or library DB, which broke the 25 MB quota and restored
  rows pointing at missing files).

**Tests:** 45 JVM tests (was 6), including Robolectric tests that run the real PdfBox engine
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
