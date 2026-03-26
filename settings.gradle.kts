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
include(":models")
include(":api")
include(":observer")
include(":auth")
include(":retryable-http-client")
include(":secure-http-client")
include(":events-tracking")
include(":evaluation")
include(":streaming-thin")
include(":e2e")
// include(":sdk")

listOf<String>(
    "fallback",
    "logger",
    "http",
    "http-api",
    "backoff",
    "tracker",
    "events",
    "streaming",
    "streaming-support",
).forEach {
    include(":$it")
    project(":$it").projectDir = file("android-client/$it")
}
