// settings.gradle.kts

// This block configures where Gradle looks for plugins.
pluginManagement {
    repositories {
        google()              // Google's Maven repository for Android plugins
        mavenCentral()        // General repository for many libraries and plugins
        gradlePluginPortal()  // The official Gradle plugin portal
    }
}

// This block configures where Gradle looks for your app's dependencies (libraries).
// It also enables the version catalog feature (libs.versions.toml).
dependencyResolutionManagement {
    // This is a recommended setting that encourages defining dependencies in your version catalog.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()              // Google's Maven repository for Android libraries
        mavenCentral()        // General repository for many libraries
        maven { url = uri("https://jitpack.io") }
    }
}

// Sets the name of your root project.
rootProject.name = "CloudCounter"

// Includes your main application module (the 'app' folder) in the build.
include(":app")
