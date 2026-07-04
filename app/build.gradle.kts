plugins {
    alias(libs.plugins.sheaf.application)
    alias(libs.plugins.sheaf.compose)
    alias(libs.plugins.sheaf.hilt)
}

android {
    namespace = "com.sheaf.app"

    defaultConfig {
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
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
    implementation(project(":feature:annotate"))
    implementation(project(":feature:pages"))
    implementation(project(":feature:scan"))
    implementation(project(":feature:ai"))
    implementation(project(":feature:billing"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
