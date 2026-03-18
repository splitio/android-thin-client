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
include(":auth")
include(":retryable-http-client")
include(":secure-http-client")
include(":events-tracking")
include(":e2e")
// include(":sdk")

listOf<String>(
    "fallback",
    "logger",
    "http",
    "http-api",
    "backoff",
    "tracker",
).forEach {
    include(":$it")
    project(":$it").projectDir = file("android-client/$it")
}
