pluginManagement {
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
    }
}

rootProject.name = "logyx-android"

include(":app")
include(":core:model", ":core:network", ":core:data", ":core:designsystem")
include(":feature:auth", ":feature:loads", ":feature:requests", ":feature:shipments", ":feature:driver")
