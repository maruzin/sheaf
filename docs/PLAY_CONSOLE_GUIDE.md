# Sheaf — Play Console Upload & Compliance Guide

Everything you need when creating and submitting the app in the Google Play Console.

## App identity
- **App name:** Sheaf
- **Package / applicationId:** `com.sheaf.app`  *(fixed — must match the code)*
- **Category:** Productivity
- **Default language:** English

## In-app product (required for Sheaf Pro)
Monetization ▸ Products ▸ **In-app products** ▸ Create:
- **Product ID:** `sheaf_pro`  *(MUST match exactly — it's hard-coded in `BillingManager`)*
- **Type:** One-time purchase (managed product)
- Set a price, activate it.
- Test with a **License tester** account on the Internal testing track before production.

## Permissions & why (for the "App content" + reviewers)
- **INTERNET** — required by Google Play Billing.
- **No CAMERA permission** — scanning uses the Google Play services ML Kit Document Scanner, which runs in
  Google's own UI, so Sheaf itself declares no camera permission.
- No location, contacts, storage-broad, or background permissions.

## Data safety form (answers)
Sheaf itself sends nothing, but the bundled **Google ML Kit** SDK (OCR + document scanner) sends
diagnostics to Google, and Play requires you to declare SDK collection
(https://developers.google.com/ml-kit/android-data-disclosure). Answering "No data collected" is a
common cause of Data safety rejections. Declare:
- **Does your app collect or share any of the required user data types?** → **Yes.**
- **Is all of the user data collected by your app encrypted in transit?** → **Yes** (ML Kit uses HTTPS).
- **Do you provide a way for users to request that their data is deleted?** → **No** (no account and no
  developer-held data; ML Kit diagnostics are held by Google).
- **Data types → Collected (not shared), processed ephemerally: No, collection required: Yes,
  purpose: App functionality + Analytics (diagnostics):**
  - **App info and performance → Crash logs / Diagnostics** (ML Kit performance and error metrics).
  - **Device or other IDs** (ML Kit per-installation identifier).
- **Not collected:** personal info, financial info (Google Play Billing handles payment — Google is the
  data controller there, so you don't declare it), location, files and docs (processed on-device only),
  photos (scanner images stay on-device), messages, contacts, web history.
- Keep this in sync with `docs/PRIVACY_POLICY.md` / `privacy-policy.html` (both say the same).

## Content rating
Complete the questionnaire honestly — Sheaf is a utility with no user-generated public content, no
violence, etc. Expected rating: **Everyone**.

## Privacy policy (required URL)
Host `docs/privacy-policy.html` at a public URL and paste it into Store presence ▸ App content ▸ Privacy
policy. Easiest free option: enable **GitHub Pages** on the repo and use
`https://<your-username>.github.io/sheaf/privacy-policy.html`, or paste the text into any free host.

## Store listing assets you'll need to prepare
- **App icon:** 512×512 PNG (32-bit).
- **Feature graphic:** 1024×500 PNG/JPG.
- **Screenshots:** at least 2 phone screenshots (min 320px, 16:9 or 9:16). Capture on a device/emulator:
  library, reader, annotate, scan, forms, paywall.
- **Short description** (≤80 chars) and **Full description** (≤4000 chars).

## Upload steps (Internal testing first)
1. Create the app in Play Console (name, language, app/game = App, free/paid = Free).
2. Complete: App access, Ads (No ads), Content rating, Target audience, Data safety, Privacy policy.
3. Testing ▸ **Internal testing** ▸ Create release.
4. Upload `app-release.aab`. Play App Signing will enroll on first upload.
5. Add yourself + testers (email allowlist), roll out to Internal testing.
6. Install via the tester opt-in link; verify the purchase + restore flow with a license-tester account.
7. When satisfied, promote to Closed/Open testing, then Production.

## Common first-submission blockers (avoid these)
- Product ID mismatch (must be exactly `sheaf_pro`).
- Missing privacy policy URL.
- targetSdk too low — since 31 Aug 2026 new apps and updates must target **API 36 (Android 16)**.
  Sheaf targets **36**. ✔
- Play Billing Library too old — since 31 Aug 2026, **v8 or later** is required. Sheaf uses **8.3.0**. ✔
- 16 KB memory pages — required for apps targeting Android 15+. All bundled native libs (ML Kit OCR,
  DataStore, Compose graphics) are 16 KB-aligned (checked with `zipalign -c -P 16`). ✔
- versionCode must go up on every upload (currently **3**, in `AndroidApplicationConventionPlugin.kt`).
- Data safety says "no data" while ML Kit sends diagnostics — see the answers above.
- Store listing / onboarding mentioning features that aren't shipped (e.g. AI chat) → misleading claims.
- Testing the purchase on a non-license-tester account (it will attempt a real charge).
