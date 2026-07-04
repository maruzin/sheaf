plugins {
    alias(libs.plugins.sheaf.library)
    alias(libs.plugins.sheaf.compose)
}
android {
    namespace = "com.sheaf.core.ui"
}
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material.icons.extended)
}
