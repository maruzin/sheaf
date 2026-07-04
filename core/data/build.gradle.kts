plugins {
    alias(libs.plugins.sheaf.library)
    alias(libs.plugins.sheaf.hilt)
}
android {
    namespace = "com.sheaf.core.data"
}
dependencies {
    implementation(project(":core:domain"))
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
