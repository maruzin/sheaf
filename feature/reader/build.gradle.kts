plugins {
    alias(libs.plugins.sheaf.feature)
}
android {
    namespace = "com.sheaf.feature.reader"
}
dependencies {
    implementation(project(":core:data"))
    // Rendering engine: provisional PDFium fork. FINAL decision recorded in BUILD_NOTES at M1 start
    // after benchmarking vs androidx.pdf against the 100MB / 1000-page budgets.
    implementation(libs.pdfium.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material.icons.extended)
}
