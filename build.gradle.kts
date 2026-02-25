plugins {
    id("com.android.library") version "9.0.0" apply false
    id("com.android.fused-library") version "9.0.0" apply false
    id("org.jetbrains.kotlin.android") version "1.8.10" apply false
    id("com.vanniktech.maven.publish") version "0.34.0" apply false
}

subprojects {
    group = "io.split.client"
    version = "0.1.0"
}

tasks.register("publishAndroidThinClientToMavenLocal") {
    group = "publishing"
    description = "Publishes the fused artifact to mavenLocal for consumer androidTest module"
    dependsOn(":android-thin-client:publishToMavenLocal")
}

tasks.register("verifyAndroidTestConsumesMavenLocal") {
    group = "verification"
    description = "Publishes fused artifact to mavenLocal and compiles consumer androidTest against it"
    dependsOn(":android-thin-client:publishToMavenLocal")
    dependsOn(":consumer-androidtest:compileDebugAndroidTestKotlin")
}
