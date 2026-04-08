plugins {
    id("com.android.library")
}

android {
    namespace = "io.split.client.thin.consumer"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

tasks.configureEach {
    if (name == "connectedCheck" || (name.startsWith("connected") && name.contains("AndroidTest"))) {
        dependsOn(":android-thin-client:publishToMavenLocal")
    }
}

dependencies {
    // Thin client dep
    androidTestImplementation("io.split.client:android-thin-client:0.1.0")

    // "Regular" SDK to verify side-by-side build
    androidTestImplementation("io.split.client:android-client:5.5.0")

    // Room (required by thin client persistence at runtime)
    androidTestImplementation("androidx.room:room-runtime:2.4.3")

    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.10.0")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.2.0")
}
