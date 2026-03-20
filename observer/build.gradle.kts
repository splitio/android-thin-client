plugins {
    id("com.android.library")
}

android {
    namespace = "io.split.client.thin.observer"
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
    implementation(project(":logger"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:4.8.0")
    testImplementation("org.mockito:mockito-inline:4.8.0")
}
