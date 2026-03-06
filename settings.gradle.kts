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
// include(":sdk")
include(":android-client:fallback")
include(":android-client:logger")
