pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Removing the repositoriesMode line that causes incubating API warnings
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MoodTracker"
include(":app")

rootProject.name = "MoodTracker"
include(":app")