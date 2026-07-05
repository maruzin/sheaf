plugins {
    alias(libs.plugins.sheaf.feature)
}
android {
    namespace = "com.sheaf.feature.reader"
}
dependencies {
    implementation(project(":core:data"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    // Text engine for full-text search + (later) outline extraction.
    implementation(libs.pdfbox.android)
    // M5: on-device document scanner (capture, auto-crop, multi-page → PDF) + OCR text recognition.
    implementation(libs.mlkit.doc.scanner)
    implementation(libs.mlkit.text.recognition)
    // M1 baseline renders via the platform android.graphics.pdf.PdfRenderer — no external engine dep.
    // PDFium / androidx.pdf are the production-engine candidates (benchmark), swappable behind
    // PdfRenderSource. See BUILD_NOTES.md.
}
