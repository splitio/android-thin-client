import com.android.build.api.dsl.LibraryExtension
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

buildscript {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    dependencies {
        classpath("com.gradleup.shadow:shadow-gradle-plugin:9.4.0")
    }
}

plugins {
    jacoco
    id("com.android.library") version "9.0.0" apply false
    id("com.android.fused-library") version "9.0.0" apply false
    id("org.jetbrains.kotlin.android") version "1.8.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.8.10" apply false
    id("com.vanniktech.maven.publish") version "0.34.0" apply false
}

subprojects {
    group = "io.split.client"
    version = "1.0.0-rc6"

    plugins.withId("com.android.library") {
        extensions.configure<LibraryExtension>("android") {
            compileSdk = 34
            buildTypes.named("debug") {
                enableUnitTestCoverage = true
            }
            testOptions {
                unitTests.all {
                    it.extensions.configure(JacocoTaskExtension::class.java) {
                        isIncludeNoLocationClasses = true
                        excludes = listOf("jdk.internal.*")
                    }
                }
            }
        }
    }
}

tasks.register<JacocoReport>("jacocoAggregateUnitTestReport") {
    group = "verification"
    description = "Generates an aggregate JaCoCo report across all modules."

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    val coverageExclusions = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*"
    )

    val coverageModules = subprojects.filter { it.name != "e2e" }

    classDirectories.setFrom(
        files(
            coverageModules.map { module ->
                fileTree(module.layout.buildDirectory.get().asFile) {
                    include(
                        "tmp/kotlin-classes/debug/**/*.class",
                        "intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes/**/*.class",
                        "intermediates/javac/debug/compileDebugJavaWithJavac/classes/**/*.class"
                    )
                    exclude(coverageExclusions)
                }
            }
        )
    )

    sourceDirectories.setFrom(
        files(
            coverageModules.flatMap { module ->
                listOf(
                    module.file("src/main/java"),
                    module.file("src/main/kotlin")
                )
            }
        )
    )

    executionData.setFrom(
        fileTree(rootDir) {
            include(
                "**/build/**/*.exec",
                "**/build/**/*.ec"
            )
            // android-client is a git submodule; exclude any of its coverage artifacts from this repo's report.
            exclude("android-client/**")
        }
    )

    subprojects.forEach { module ->
        dependsOn(module.tasks.matching { it.name == "testDebugUnitTest" })
    }
}

tasks.register("publishAndroidThinClientToMavenLocal") {
    group = "publishing"
    description = "Publishes the fused artifact to mavenLocal for consumer androidTest module"
    dependsOn(":android-thin-client:publishToMavenLocal")
}

tasks.register<Exec>("publishToMavenLocalForE2e") {
    group = "publishing"
    description = "Publishes the fused AAR with MIN_EVALUATION_REFRESH_RATE=1 and near-zero CDN bypass backoff for e2e consumption"
    commandLine(
        "./gradlew",
        ":android-thin-client:publishToMavenLocal",
        "-PminEvaluationRefreshRate=1",
        "-PcdnBypassBackoffBaseMs=1",
    )
}

tasks.register("verifyAndroidTestConsumesMavenLocal") {
    group = "verification"
    description = "Publishes fused artifact to mavenLocal and compiles consumer androidTest against it"
    dependsOn(":android-thin-client:publishToMavenLocal")
    dependsOn(":consumer-androidtest:compileDebugAndroidTestKotlin")
}
