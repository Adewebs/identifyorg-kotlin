pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // io.livekit:livekit-android transitively depends on
        // com.github.davidliu:audioswitch, which is only published on
        // JitPack, not Maven Central/Google — required for LiveKit's
        // Android SDK to resolve at all.
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "identifyorg-kotlin"
