plugins {
    alias(libs.plugins.sheaf.library)
    alias(libs.plugins.sheaf.hilt)
}
android {
    namespace = "com.sheaf.core.domain"
}
dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
