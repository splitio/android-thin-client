import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.tasks.Jar

plugins {
    id("com.android.fused-library")
    id("com.vanniktech.maven.publish")
}

apply(from = rootProject.file("gradle/shadow-repackage.gradle.kts"))

androidFusedLibrary {
    namespace = "io.split.client.thin"
    minSdk {
        version = release(21)
    }
}

val fusedIncludedProjects = listOf(
    project(":models"),
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
    project(":secure-http-client"),
    project(":evaluation"),
    project(":cdn-bypass"),
    project(":persistence"),
    project(":observer"),
    project(":events"),
    project(":streaming-thin"),
    project(":streaming"),
    project(":streaming-support"),
    project(":executor"),
    project(":persistence-domain"),
    project(":submitter"),
)

dependencies {
    fusedIncludedProjects.forEach { include(it) }
}

val javadocJar = tasks.register("javadocJar", Jar::class.java) {
    archiveBaseName.set("android-thin-client")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("javadoc")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
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

    pom {
        name.set("Split Android Thin Client SDK")
        description.set("Official Split Android Thin Client SDK.")
        url.set("https://github.com/splitio/android-thin-client")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("sdks")
                email.set("sdks@split.io")
            }
        }

        scm {
            connection.set("scm:git:git@github.com:splitio/android-thin-client.git")
            developerConnection.set("scm:git@github.com:splitio/android-thin-client.git")
            url.set("https://github.com/splitio/android-thin-client")
        }
    }
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

val repackageAar = tasks.named("repackageAar")

afterEvaluate {
    tasks.configureEach {
        if (name.startsWith("generateMetadataFileFor") ||
            name == "generateModuleMetadata" ||
            javaClass.name.endsWith("GenerateModuleMetadata")
        ) {
            enabled = false
        }

        // Make all publish tasks depend on repackageAar so the repackaged AAR is ready
        if (name.startsWith("publish") || name == "publishToMavenLocal") {
            dependsOn(repackageAar)
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

            // Ensure javadoc artifact is present (required by Maven Central).
            artifacts.removeAll { it.classifier == "javadoc" && it.extension == "jar" }
            artifact(javadocJar)

            // Replace the default AAR with the repackaged one
            val repackagedAar = layout.buildDirectory
                .file("outputs/aar/android-thin-client-release-repackaged.aar")
                .get().asFile
            artifacts.removeAll { it.extension == "aar" }
            artifact(repackagedAar) {
                extension = "aar"
                builtBy(repackageAar)
            }
        }
    }

    // Wire signArtifacts: runs after staging publish, before upload to Maven Central
    val signTask = tasks.findByName("signArtifacts")
    val publishTask = tasks.findByName("publishAllPublicationsToMavenCentralRepository")
    if (signTask != null && publishTask != null) {
        signTask.dependsOn(publishTask)
        publishTask.finalizedBy(signTask)
    }
}

tasks.register("signArtifacts") {
    description = "Signs all Maven artifacts with GPG"
    group = "publishing"

    doLast {
        val groupPath = project.group.toString().replace('.', '/')
        val artifactId = "android-thin-client"
        val versionStr = project.version.toString()
        val artifactDir = file("${buildDir}/publishing/mavenCentral/${groupPath}/${artifactId}/${versionStr}")

        if (!artifactDir.exists()) {
            println("WARNING: Artifact directory does not exist: $artifactDir")
            println("Skipping signing. Run publishAllPublicationsToMavenCentralRepository first.")
            return@doLast
        }

        val keyId = project.findProperty("signing.keyId") as String?
            ?: throw GradleException("signing.keyId property is not set in gradle.properties")
        val password = project.findProperty("signing.password") as String?
            ?: throw GradleException("signing.password property is not set in gradle.properties")

        val gpgPaths = listOf(
            "/opt/homebrew/bin/gpg",
            "/usr/local/bin/gpg",
            "/usr/bin/gpg",
            "/opt/local/bin/gpg",
        )
        var gpgCommand: String? = gpgPaths.firstOrNull { java.io.File(it).canExecute() }
        if (gpgCommand == null) {
            try {
                val proc = ProcessBuilder("gpg", "--version").redirectErrorStream(true).start()
                if (proc.waitFor() == 0) gpgCommand = "gpg"
            } catch (_: Exception) {}
        }
        if (gpgCommand == null) {
            throw GradleException(
                "GPG not found. Install with: brew install gnupg\nSearched: ${gpgPaths.joinToString()}"
            )
        }

        val artifactsToSign = artifactDir.listFiles()
            ?.filter { f -> (f.name.endsWith(".aar") || f.name.endsWith(".jar") || f.name.endsWith(".pom")) && !f.name.endsWith(".asc") }
            ?: emptyList()

        if (artifactsToSign.isEmpty()) {
            println("WARNING: No artifacts found to sign in $artifactDir")
            return@doLast
        }

        println("Signing ${artifactsToSign.size} artifact(s) with GPG key $keyId")
        artifactsToSign.forEach { artifact ->
            val ascFile = java.io.File(artifact.parentFile, "${artifact.name}.asc")
            if (ascFile.exists()) {
                println("  Already signed: ${artifact.name}")
                return@forEach
            }
            val cmd = listOf(
                gpgCommand, "--batch", "--yes", "--no-tty",
                "--passphrase", password,
                "--pinentry-mode", "loopback",
                "--local-user", keyId,
                "--detach-sign", "--armor",
                artifact.absolutePath,
            )
            val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
            val exit = proc.waitFor()
            if (exit != 0) {
                throw GradleException("Failed to sign ${artifact.name}: exit $exit\n${proc.inputStream.bufferedReader().readText()}")
            }
            println("  Signed: ${artifact.name}")
        }
    }
}
