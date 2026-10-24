plugins {
    alias(libs.plugins.sheaf.library)
    alias(libs.plugins.sheaf.hilt)
}
android {
    namespace = "com.sheaf.core.data"
}
// Export Room schemas so every future version bump ships a tested Migration (no destructive resets).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
dependencies {
    implementation(project(":core:domain"))
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.androidx.core.ktx)
    // M7: Google Play Billing for the Pro (freemium) unlock.
    implementation(libs.billing.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
