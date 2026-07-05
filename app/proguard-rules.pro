# ---- Sheaf release keep rules (R8 / proguard-android-optimize) ----

# Keep annotation/signature metadata used by reflection-based libraries.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# PdfBox-Android (tom_roush): heavy reflection + bundled font/resource loading.
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**

# ML Kit document scanner + Latin text recognition.
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Google Play Billing.
-keep class com.android.billingclient.** { *; }

# Kotlin coroutines internals (defensive; consumer rules usually cover these).
-dontwarn kotlinx.coroutines.**

# Hilt, Room, DataStore, and Compose ship their own consumer ProGuard rules.
