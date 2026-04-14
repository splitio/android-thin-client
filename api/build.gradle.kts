import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    id("com.android.library")
    id("jacoco")
}

android {
    namespace = "io.split.client.thin"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
            buildConfigField("int", "MIN_EVALUATION_REFRESH_RATE", "1")
        }
        release {
            buildConfigField("int", "MIN_EVALUATION_REFRESH_RATE", "60")
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
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.extensions.configure(JacocoTaskExtension::class.java) {
                isIncludeNoLocationClasses = true
                excludes = listOf("jdk.internal.*")
            }
        }
    }
}

dependencies {
    api(project(":models"))
    api(project(":fallback"))
    implementation(project(":logger"))
    implementation(project(":events-tracking"))
    implementation(project(":evaluation"))
    implementation(project(":secure-http-client"))
    implementation(project(":streaming-thin"))
    implementation(project(":streaming"))
    implementation(project(":backoff"))
    implementation(project(":retryable-http-client"))
    implementation(project(":auth"))
    implementation(project(":observer"))
    implementation(project(":persistence-domain"))
    implementation(project(":events"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-process:2.5.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.mockito:mockito-core:4.8.0")
    testImplementation("org.mockito:mockito-inline:4.8.0")
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
