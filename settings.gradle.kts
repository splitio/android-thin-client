pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("android-client/gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "android-thin-client"

include(":android-thin-client")
include(":api")
include(":retryable-http-client")
// include(":sdk")

listOf<String>(
    "fallback",
    "logger",
    "http",
    "http-api",
    "backoff"
).forEach {
    include(":android-client:$it")
}
