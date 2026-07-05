# Sheaf — Building the Release AAB

## Do I need Android Studio?
**To upload to Google Play you must produce a signed release App Bundle (`.aab`).** GitHub CI only builds
an *unsigned debug* APK — it never produces the release AAB. So yes, you build locally at least once. Two
options:

- **Android Studio (recommended)** — also lets you run Sheaf on an emulator/real device for QA.
- **Command line** — `./gradlew bundleRelease` (needs JDK 17 + Android SDK; Android Studio installs both).

Either way you need JDK 17 and the Android SDK (compileSdk/targetSdk **35**).

## One-time: point the build at your keystore
Your release keystore is the file you uploaded ("DeanPlay" — a PKCS#12/Java keystore). Keep it somewhere
safe **outside** the repo (e.g. `~/keys/DeanPlay`). If you lose it, you can never update the app again.

1. Copy `keystore.properties.template` (repo root) to `keystore.properties` (same folder).
2. Fill it in:
   ```
   storeFile=/Users/deanmaruzin/keys/DeanPlay
   storePassword=…            # the keystore password you set
   keyAlias=…                 # see command below to find it
   keyPassword=…              # often the same as storePassword
   ```
3. `keystore.properties`, `*.jks`, and `*.keystore` are gitignored — they will not be committed.

Find your alias / verify the keystore (run on your Mac; it will prompt for the store password):
```
keytool -list -v -keystore /Users/deanmaruzin/keys/DeanPlay -storetype PKCS12
```

## Build the AAB

### Android Studio
1. Open the project folder in Android Studio; let Gradle sync.
2. **Build ▸ Generate Signed App Bundle / APK ▸ Android App Bundle**.
3. Choose your keystore ("DeanPlay"), enter passwords, pick the **release** build variant, Finish.
4. Output: `app/release/app-release.aab`.

### Command line
```
cd "/Users/deanmaruzin/AI CLAUDE DEVELOPMENT/PDF App"
./gradlew bundleRelease
# → app/build/outputs/bundle/release/app-release.aab
```
(With `keystore.properties` present, the AAB is signed automatically.)

## Before your first upload
- Bump `versionCode`/`versionName` in `build-logic/.../ProjectExtensions.kt`-adjacent config for each
  release (currently versionCode 1 / versionName "0.1.0").
- Test the signed build on a device — CI cannot run the app, so device QA (camera scan, OCR, and the
  purchase flow especially) happens here. See `Sheaf - Release Checklist` in your vault.

## Play App Signing
Google Play uses **Play App Signing**: you upload with your *upload key* (this keystore); Google holds the
final app signing key. Keep the keystore anyway — you need it for every future upload.
