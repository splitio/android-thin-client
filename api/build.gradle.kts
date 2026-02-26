import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    id("com.android.library")
    id("jacoco")
}

android {
    namespace = "io.split.client.thin"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
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

dependencies {
    implementation("com.android.support:appcompat-v7:28.0.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("com.android.support.test:runner:1.0.2")
    androidTestImplementation("com.android.support.test.espresso:espresso-core:3.0.2")
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.register<JacocoReport>("jacocoDebugUnitTestReport") {
    dependsOn("testDebugUnitTest")
    val moduleBuildDir = layout.buildDirectory.get().asFile

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

    classDirectories.setFrom(
        files(
            fileTree(moduleBuildDir) {
                include(
                    "tmp/kotlin-classes/debug/**/*.class",
                    "intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes/**/*.class"
                )
                exclude(coverageExclusions)
            },
            fileTree(moduleBuildDir.resolve("intermediates/javac/debug/compileDebugJavaWithJavac/classes")) {
                exclude(coverageExclusions)
            }
        )
    )

    sourceDirectories.setFrom(
        files(
            "src/main/java",
            "src/main/kotlin"
        )
    )

    executionData.setFrom(
        fileTree(moduleBuildDir) {
            include("**/*.exec", "**/*.ec")
        }
    )
}
