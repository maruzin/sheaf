package com.sheaf.convention

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("com.android.application")
            apply("org.jetbrains.kotlin.android")
        }
        extensions.configure<ApplicationExtension> {
            configureKotlinAndroid(this)
            defaultConfig {
                applicationId = AppConfig.APPLICATION_ID
                targetSdk = AppConfig.TARGET_SDK
                // Bump versionCode on EVERY build you upload to Play (each upload must be unique + higher).
                versionCode = 2
                versionName = "1.0.0"
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
        }
    }
}
