import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.tasks.Jar

plugins {
    id("com.android.fused-library")
    id("com.vanniktech.maven.publish")
}

androidFusedLibrary {
    namespace = "io.split.client.thin"
    minSdk {
        version = release(21)
    }
}
val fusedIncludedProjects = listOf(
    project(":api"),
    project(":fallback"),
    project(":logger"),
    project(":backoff"),
    project(":http"),
    project(":http-api"),
    project(":retryable-http-client"),
    project(":tracker"),
    project(":events-tracking"),
    project(":auth"),
)

dependencies {
    fusedIncludedProjects.forEach { include(it) }
}

val fusedSourcesJar = tasks.register("fusedSourcesJar", Jar::class.java) {
    archiveBaseName.set("android-thin-client")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("sources")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))

    from(
        fusedIncludedProjects.flatMap { sourceProject ->
            listOf(
                sourceProject.file("src/main/java"),
                sourceProject.file("src/main/kotlin"),
            )
        }.filter { it.exists() }
    )
}

mavenPublishing {
    coordinates("io.split.client", "android-thin-client", project.version.toString())
    publishToMavenCentral(false)
}

tasks.register("buildAndPublishToMavenLocal") {
    group = "publishing"
    description = "Builds the fused library and then publishes it to Maven Local."
    dependsOn(
        tasks.matching {
            it.name == "build" || it.name == "bundle" || it.name == "assemble"
        }
    )
    finalizedBy("publishToMavenLocal")
}

tasks.configureEach {
    if (name == "build" || name == "bundle" || name == "assemble") {
        finalizedBy("publishToMavenLocal")
    }
}

afterEvaluate {
    tasks.configureEach {
        if (name.startsWith("generateMetadataFileFor") ||
            name == "generateModuleMetadata" ||
            javaClass.name.endsWith("GenerateModuleMetadata")
        ) {
            enabled = false
        }
    }

    publishing.publications.withType(MavenPublication::class.java).configureEach {
        if (name == "maven") {
            listOf("sources", "javadoc").forEach { classifierName ->
                val duplicates = artifacts
                    .filter { it.classifier == classifierName && it.extension == "jar" }
                    .drop(1)
                duplicates.forEach { artifact ->
                    artifacts.remove(artifact)
                    println("Removed duplicate $classifierName artifact: ${artifact.file?.name}")
                }
            }

            // Ensure publication always includes one concrete merged sources artifact.
            artifacts.removeAll { it.classifier == "sources" && it.extension == "jar" }
            artifact(fusedSourcesJar)
        }
    }
}
