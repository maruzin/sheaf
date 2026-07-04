pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "Sheaf"

include(":app")
include(":core:ui")
include(":core:domain")
include(":core:data")
include(":feature:reader")
include(":feature:annotate")
include(":feature:pages")
include(":feature:scan")
include(":feature:ai")
include(":feature:billing")
