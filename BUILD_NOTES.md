# Sheaf — Build Notes

Running engineering log. Newest first. Every non-obvious decision gets one line with *why*.

---

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
Open the project in Android Studio and run the first `./gradlew assembleDebug` to confirm the module
graph + convention plugins resolve, fixing any version/wrapper nits. Then begin M1: implement the
PDFium `PdfRenderSourceFactory`, benchmark vs androidx.pdf, and build the library + reader screens.
