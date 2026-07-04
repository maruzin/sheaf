package com.sheaf.convention

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Convention for :feature:* modules — applies library + compose + hilt and wires the
 * common core modules and lifecycle/navigation deps every feature needs.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("sheaf.android.library")
            apply("sheaf.android.compose")
            apply("sheaf.android.hilt")
        }
        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        dependencies {
            add("implementation", project(":core:ui"))
            add("implementation", project(":core:domain"))
            add("implementation", libs.findLibrary("androidx-lifecycle-runtime-ktx").get())
            add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
            add("implementation", libs.findLibrary("androidx-navigation-compose").get())
            add("implementation", libs.findLibrary("hilt-navigation-compose").get())
            add("testImplementation", libs.findLibrary("junit").get())
            add("testImplementation", libs.findLibrary("mockk").get())
            add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
            add("testImplementation", libs.findLibrary("turbine").get())
        }
    }
}
