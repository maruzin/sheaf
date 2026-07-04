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
                versionCode = 1
                versionName = "0.1.0"
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
        }
    }
}
