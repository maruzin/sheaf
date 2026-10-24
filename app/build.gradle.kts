import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.sheaf.application)
    alias(libs.plugins.sheaf.compose)
    alias(libs.plugins.sheaf.hilt)
    alias(libs.plugins.baselineprofile)
}

// Release signing is read from an untracked keystore.properties at the repo root (see
// keystore.properties.template). Absent on CI (which only builds debug), so this stays optional.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.sheaf.app"

    defaultConfig {
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Bundle native debug symbols so Play can symbolicate native crashes/ANRs.
            ndk { debugSymbolLevel = "FULL" }
            // Uses the release keystore when keystore.properties is present.
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
        // Build types the Baseline Profile plugin adds for benchmarking. Without a release keystore
        // (CI, fresh clones) sign them with the debug key so they can be installed on an emulator.
        // They are never uploaded to Play.
        configureEach {
            if (name == "benchmarkRelease" || name == "nonMinifiedRelease") {
                if (!keystorePropertiesFile.exists()) signingConfig = signingConfigs.getByName("debug")
            }
        }
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    // Core
    implementation(project(":core:ui"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    // Features
    implementation(project(":feature:reader"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.material.icons.extended)

    // Installs the app's Baseline Profile (and the profiles shipped by AndroidX libraries) at install
    // time, so startup and first scroll run precompiled code instead of interpreting.
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":benchmark"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.core)
    // Builds real (and password-protected) PDFs inside the end-to-end tests.
    androidTestImplementation(libs.pdfbox.android)
    androidTestImplementation(libs.androidx.espresso.core)
}
