plugins {
    `kotlin-dsl`
}
group = "com.sheaf.buildlogic"

dependencies {
    compileOnly(libs.plugins.android.application.toDep())
    compileOnly(libs.plugins.android.library.toDep())
    compileOnly(libs.plugins.kotlin.android.toDep())
    compileOnly(libs.plugins.kotlin.compose.toDep())
    compileOnly(libs.plugins.ksp.toDep())
    compileOnly(libs.plugins.hilt.toDep())
}

// Helper to reference plugin artifacts from the version catalog inside build-logic.
fun Provider<PluginDependency>.toDep() = map {
    "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}"
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "sheaf.android.application"
            implementationClass = "com.sheaf.convention.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "sheaf.android.library"
            implementationClass = "com.sheaf.convention.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "sheaf.android.compose"
            implementationClass = "com.sheaf.convention.AndroidComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "sheaf.android.hilt"
            implementationClass = "com.sheaf.convention.HiltConventionPlugin"
        }
        register("androidFeature") {
            id = "sheaf.android.feature"
            implementationClass = "com.sheaf.convention.AndroidFeatureConventionPlugin"
        }
    }
}
