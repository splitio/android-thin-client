plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "io.split.client.thin.internal.persistence"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
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

dependencies {
    implementation("androidx.room:room-runtime:2.4.3")
    implementation("androidx.lifecycle:lifecycle-livedata:2.5.1")
    annotationProcessor("androidx.room:room-compiler:2.4.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.8.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.8.0")
    testImplementation("org.mockito:mockito-core:4.8.0")
    testImplementation("org.mockito:mockito-inline:4.8.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("org.robolectric:robolectric:4.10.3")
}
