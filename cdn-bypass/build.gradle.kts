plugins {
    id("com.android.library")
}

android {
    namespace = "io.split.client.thin.cdnbypass"

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("int", "CDN_BYPASS_BACKOFF_BASE_MS", cdnBypassBackoffBaseMs())
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
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
}

fun cdnBypassBackoffBaseMs(): String {
    return (project.findProperty("cdnBypassBackoffBaseMs") as? String) ?: "1000"
}

dependencies {
    implementation(project(":backoff"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
