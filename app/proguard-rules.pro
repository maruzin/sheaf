# ---- Sheaf release keep rules (R8 / proguard-android-optimize) ----

# Keep annotation/signature metadata used by reflection-based libraries.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# PdfBox-Android (tom_roush): heavy reflection + bundled font/resource loading.
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**

# ML Kit Latin text recognition: kept whole because OCR loads its pipeline reflectively and can't be
# exercised without Pro on a test device. Revisit with R8 Configuration Analyzer.
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Google Play services (document scanner) and Play Billing ship their own consumer R8 rules. A blanket
# keep of com.google.android.gms.** pinned ~27,000 entries (58% of all keeps) and blocked R8
# optimisation (Play Console "Improve memory and performance with R8"), so it was removed.
-dontwarn com.google.android.gms.**

# Kotlin coroutines internals (defensive; consumer rules usually cover these).
-dontwarn kotlinx.coroutines.**

# Hilt, Room, DataStore, and Compose ship their own consumer ProGuard rules.
