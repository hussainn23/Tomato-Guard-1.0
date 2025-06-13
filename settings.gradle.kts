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
        jcenter()  // If uCrop isn't resolving, add this

    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        jcenter()  // If uCrop isn't resolving, add this
        maven { url = uri("https://jitpack.io") }
        mavenCentral()
    }
}

rootProject.name = "Practice"
include(":app")
 