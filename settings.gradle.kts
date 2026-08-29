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
    }
}

rootProject.name = "PokemonScanner"
include(
    ":app",
    ":core-model",
    ":core-domain",
    ":core-database",
    ":core-image",
    ":automation-debug",
)
