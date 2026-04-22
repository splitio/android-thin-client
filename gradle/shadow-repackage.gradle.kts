/**
 * Script plugin that repackages the fused AAR using Shadow plugin.
 *
 * All submodule classes (io.split.android.client.*, io.harness.events.*, etc.) are relocated
 * into io.split.client.thin.repackaged.* to eliminate classpath conflicts when a consumer
 * depends on both this SDK and the full android-client SDK.
 *
 * Unlike R8's -repackageclasses, Shadow rewrites ALL bytecode references (including in kept
 * classes), fixing ClassNotFoundException at runtime when public API classes reference
 * relocated android-client classes.
 *
 * Registers three tasks on the applying project:
 *  - extractAarForRepackaging — extracts classes.jar from the fused AAR
 *  - shadowClassesJar         — applies Shadow relocation to classes.jar
 *  - repackageAar             — re-zips the AAR with the shadowed classes.jar
 *
 * Also registers:
 *  - verifyRepackaging — asserts no original submodule packages remain in the output
 *
 * Publishing is wired automatically: publish tasks depend on repackageAar and the repackaged
 * AAR replaces the default one in the Maven publication.
 *
 * Usage:
 *   apply(from = rootProject.file("gradle/shadow-repackage.gradle.kts"))
 */

buildscript {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    dependencies {
        classpath("com.gradleup.shadow:shadow-gradle-plugin:9.4.0")
    }
}

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

// ── Shared paths ──────────────────────────────────────────────────────────────

val workDir = layout.buildDirectory.dir("repackage-work")
val shadowTempDir = layout.buildDirectory.dir("shadow-temp")
val classesExtractDir = layout.buildDirectory.dir("classes-extracted")
val outputAarFile = layout.buildDirectory.file("outputs/aar/android-thin-client.aar")
val repackagedAarFile = layout.buildDirectory.file("outputs/aar/android-thin-client-release-repackaged.aar")

// ── Task 1: Extract AAR ───────────────────────────────────────────────────────

val extractAarForRepackaging by tasks.registering {
    group = "build"
    description = "Extracts the fused AAR contents and classes.jar for Shadow repackaging."

    dependsOn("bundle")

    inputs.file(outputAarFile)
    outputs.dir(workDir)
    outputs.dir(classesExtractDir)

    doLast {
        val aarFile = outputAarFile.get().asFile
        require(aarFile.exists()) { "AAR not found: ${aarFile.absolutePath}" }

        val workDirectory = workDir.get().asFile
        workDirectory.deleteRecursively()
        workDirectory.mkdirs()

        ZipFile(aarFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val outFile = File(workDirectory, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        outFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }

        val classesJar = File(workDirectory, "classes.jar")
        require(classesJar.exists()) {
            "classes.jar not found in AAR: ${aarFile.absolutePath}"
        }

        // Extract classes.jar to a directory for Shadow to process
        val classesDir = classesExtractDir.get().asFile
        classesDir.deleteRecursively()
        classesDir.mkdirs()

        ZipFile(classesJar).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val outFile = File(classesDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        outFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }
}

// ── Task 2: Shadow classes.jar ────────────────────────────────────────────────

/**
 * Relocates android-client submodule packages to prevent classpath conflicts.
 *
 * This SDK merges modules from the android-client git submodule (fallback, http, logger, backoff,
 * events) into the published AAR. Without relocation, consumers depending on both this thin client
 * and the full android-client SDK would encounter duplicate classes at runtime, leading to
 * unpredictable behavior or ClassCastException.
 *
 * Shadow rewrites ALL bytecode references (class names, method signatures, field types, string
 * constants) so that thin client's public API classes can correctly reference the relocated
 * android-client classes. This is critical: unlike ProGuard's -repackageclasses, Shadow updates
 * references in kept classes, preventing ClassNotFoundException at runtime.
 *
 * Relocation rules:
 * - `io.split.android.client.*` → `io.split.client.thin.repackaged.io.split.android.client.*`
 * - `io.split.android.engine.*` → `io.split.client.thin.repackaged.io.split.android.engine.*`
 * - `io.harness.events.*` → `io.split.client.thin.repackaged.io.harness.events.*`
 *
 * Note: androidx.room is NOT repackaged; consumers must provide Room 2.4.3+ as a runtime dependency.
 */
val shadowClassesJar by tasks.registering(ShadowJar::class) {
    group = "shadow"
    description = "Applies Shadow relocation to classes.jar extracted from the fused AAR."

    dependsOn(extractAarForRepackaging)

    archiveBaseName.set("classes-shadowed")
    archiveClassifier.set("")  // No classifier - replaces original classes.jar
    destinationDirectory.set(shadowTempDir)

    from(classesExtractDir)

    // Relocate android-client and harness packages to isolated namespace
    relocate("io.split.android.client", "io.split.client.thin.repackaged.io.split.android.client")
    relocate("io.split.android.engine", "io.split.client.thin.repackaged.io.split.android.engine")
    relocate("io.harness.events", "io.split.client.thin.repackaged.io.harness.events")

    // Merge META-INF/services files - required for ServiceLoader to discover relocated providers
    mergeServiceFiles()

    // Exclude signature files - they become invalid after bytecode modification and would cause
    // signature verification failures in consumer apps
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC")
}

// ── Task 3: Re-zip AAR ────────────────────────────────────────────────────────

val repackageAar by tasks.registering {
    group = "build"
    description = "Repackages the fused AAR using Shadow plugin to relocate submodule classes."

    dependsOn(shadowClassesJar)

    val shadowedJar = shadowClassesJar.flatMap { it.archiveFile }

    inputs.file(shadowedJar)
    inputs.dir(workDir)
    outputs.file(repackagedAarFile)

    doLast {
        val shadowedFile = shadowedJar.get().asFile
        require(shadowedFile.exists()) {
            "Shadow repackaging failed — output not found: ${shadowedFile.absolutePath}"
        }

        val workDirectory = workDir.get().asFile

        // Replace classes.jar with the Shadow-processed version
        val classesJar = File(workDirectory, "classes.jar")
        classesJar.delete()
        shadowedFile.copyTo(classesJar)

        // Merge consumer ProGuard rules
        val proguardFile = File(workDirectory, "proguard.txt")
        val existingRules = if (proguardFile.exists()) proguardFile.readText() else ""
        val repackageRules =
            "-keep class io.split.client.thin.** { *; }\n" +
            "-keep class io.split.client.thin.repackaged.** { *; }\n"
        proguardFile.writeText(existingRules + repackageRules)

        // Re-zip as AAR
        val repackaged = repackagedAarFile.get().asFile
        repackaged.parentFile?.mkdirs()
        ZipOutputStream(repackaged.outputStream()).use { zos ->
            workDirectory.walkTopDown().filter { it.isFile }.forEach { file ->
                val entryName = file.relativeTo(workDirectory).invariantSeparatorsPath
                zos.putNextEntry(ZipEntry(entryName))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }

        logger.lifecycle("Repackaged AAR written to: ${repackaged.absolutePath}")

        // Clean up Shadow temp directory
        shadowTempDir.get().asFile.deleteRecursively()
    }
}

// ── verifyRepackaging ─────────────────────────────────────────────────────────

tasks.register("verifyRepackaging") {
    group = "verification"
    description = "Verifies that the repackaged AAR contains no original submodule packages."

    dependsOn(repackageAar)

    val repackagedAarFile = layout.buildDirectory.file("outputs/aar/android-thin-client-release-repackaged.aar")

    doLast {
        val aarFile = repackagedAarFile.get().asFile
        require(aarFile.exists()) { "Repackaged AAR not found: ${aarFile.absolutePath}" }

        var foundForbidden = false
        var foundThinClient = false
        var foundRepackaged = false

        ZipFile(aarFile).use { aar ->
            val classesEntry = aar.getEntry("classes.jar")
                ?: throw GradleException("No classes.jar in AAR")
            val classesBytes = aar.getInputStream(classesEntry).readBytes()

            ZipFile(File.createTempFile("classes", ".jar").also {
                it.writeBytes(classesBytes)
                it.deleteOnExit()
            }).use { jar ->
                jar.entries().asSequence().filter { it.name.endsWith(".class") }.forEach { entry ->
                    val path = entry.name
                    if (path.startsWith("io/split/android/client/") ||
                        path.startsWith("io/split/android/engine/") ||
                        path.startsWith("io/harness/events/")) {
                        logger.error("FORBIDDEN class found: $path")
                        foundForbidden = true
                    }
                    if (path.startsWith("io/split/client/thin/") && !path.startsWith("io/split/client/thin/repackaged/")) {
                        foundThinClient = true
                    }
                    if (path.startsWith("io/split/client/thin/repackaged/")) {
                        foundRepackaged = true
                    }
                }
            }
        }

        if (foundForbidden) {
            throw GradleException(
                "Repackaging verification FAILED: found classes under io/split/android/ or io/harness/events/. " +
                "Shadow did not repackage all submodule classes."
            )
        }
        if (!foundThinClient) {
            throw GradleException("Repackaging verification FAILED: no classes found under io/split/client/thin/")
        }
        if (!foundRepackaged) {
            throw GradleException(
                "Repackaging verification FAILED: no classes found under io/split/client/thin/repackaged/. " +
                "Shadow may not have repackaged submodule classes."
            )
        }

        logger.lifecycle("Repackaging verification PASSED.")
    }
}

// Publishing wiring (depend on repackageAar, replace AAR artifact) is done in
// the applying build.gradle.kts via tasks.named("repackageAar"), because
// MavenPublication DSL requires the full build-script context.
