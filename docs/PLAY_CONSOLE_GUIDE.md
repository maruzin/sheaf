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
- **Does your app collect or share user data?** → **No.**
  Sheaf processes documents on-device, has no accounts/analytics/ads, and runs OCR/scanning on-device.
  Purchases go through Google Play Billing (Google handles payment data; Sheaf stores only a local
  "Pro unlocked" flag).
- **Is all data encrypted in transit?** → Not applicable (Sheaf sends no user data). The only network use
  is Google Play, which is encrypted by Google.
- **Data deletion:** No account/server data to delete.

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
- targetSdk too low — Sheaf targets **35**, which meets the current requirement. ✔
- Testing the purchase on a non-license-tester account (it will attempt a real charge).
