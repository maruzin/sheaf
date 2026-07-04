# Sheaf

An all-in-one Android PDF app — fast reader, annotate & edit, scan + OCR, and AI (Pro).
Freemium; shipped solo to Google Play.

## Modules
- `:app` — application entry, navigation, DI host.
- `:core:ui` — theme + shared Compose components.
- `:core:domain` — models + repository interfaces (framework-light).
- `:core:data` — Room, DataStore, repository implementations.
- `:feature:reader` — reader core (M1).
- `:feature:annotate` — annotations, signatures, forms (M3).
- `:feature:pages` — page management, compression, security (M4).
- `:feature:scan` — CameraX + ML Kit scan + OCR (M5).
- `:feature:ai` — Chat-with-PDF, summarize, translate (M6, Pro).
- `:feature:billing` — Play Billing, entitlements, paywall (M7).

## Build
Requires JDK 17 and Android SDK (compileSdk 35). Open in Android Studio (Ladybug+) and sync,
or from CLI:

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Convention plugins live in `build-logic/`. Dependency versions are pinned in
`gradle/libs.versions.toml`.

## Secrets
No keys in the repo. The AI feature reads a Claude API key at runtime from encrypted local
config (`sheaf-ai.properties`, git-ignored) — see BUILD_NOTES.md (M6).

## Engineering log
See `BUILD_NOTES.md` for decisions (with rationale), the feature-to-milestone map, and the
current next step.
