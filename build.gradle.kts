import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

import org.gradle.process.CommandLineArgumentProvider
import org.gradle.api.tasks.Sync
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenExec
import java.net.URI

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

evaluationDependsOn(":api")

repositories {
    mavenCentral()
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmWasi {
        nodejs()
        binaries.executable()
    }
}

val executableSuffix = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) ".exe" else ""

val wasmToolsVersion = "1.258.0"
val wasmToolsDirectory = layout.projectDirectory.dir("tools/wasm-tools/$wasmToolsVersion")
val wasmTools = wasmToolsDirectory.file("bin/wasm-tools$executableSuffix")
val wasmToolsTarget = when {
    System.getProperty("os.name").startsWith("Mac", ignoreCase = true) &&
        System.getProperty("os.arch") in setOf("aarch64", "arm64") -> "aarch64-macos"
    System.getProperty("os.name").startsWith("Mac", ignoreCase = true) -> "x86_64-macos"
    System.getProperty("os.name").startsWith("Windows", ignoreCase = true) &&
        System.getProperty("os.arch") in setOf("aarch64", "arm64") -> "aarch64-windows"
    System.getProperty("os.name").startsWith("Windows", ignoreCase = true) -> "x86_64-windows"
    System.getProperty("os.arch") in setOf("aarch64", "arm64") -> "aarch64-linux"
    System.getProperty("os.arch") in setOf("x86_64", "amd64") -> "x86_64-linux"
    else -> error("Unsupported wasm-tools host architecture: ${System.getProperty("os.arch")}")
}
val wasmToolsArchiveExtension = if (wasmToolsTarget.endsWith("windows")) "zip" else "tar.gz"
val wasmToolsUrl = "https://github.com/bytecodealliance/wasm-tools/releases/download/v$wasmToolsVersion/" +
    "wasm-tools-$wasmToolsVersion-$wasmToolsTarget.$wasmToolsArchiveExtension"

val projectWasmName = rootProject.name
val embeddedReleaseComponent = layout.buildDirectory.file(
    "intermediates/pumpkin-components/release/$projectWasmName-embedded.wasm"
)
val releaseComponent = layout.buildDirectory.file("$projectWasmName.wasm")

val installWasmTools by tasks.registering {
    group = "build setup"
    description = "Downloads the pinned wasm-tools release executable."

    inputs.property("version", wasmToolsVersion)
    inputs.property("target", wasmToolsTarget)
    inputs.property("url", wasmToolsUrl)
    outputs.file(wasmTools)

    doLast {
        val installationDirectory = wasmToolsDirectory.asFile
        val archive = temporaryDir.resolve("wasm-tools.$wasmToolsArchiveExtension")
        val installedExecutable = wasmTools.asFile
        installationDirectory.deleteRecursively()
        installationDirectory.mkdirs()
        installedExecutable.parentFile.mkdirs()

        URI(wasmToolsUrl).toURL().openStream().use { input ->
            archive.outputStream().use { output -> input.copyTo(output) }
        }
        fun runCommand(vararg command: String) {
            check(ProcessBuilder(*command).inheritIO().start().waitFor() == 0) {
                "wasm-tools archive extraction failed"
            }
        }

        if (wasmToolsTarget.endsWith("windows")) {
            runCommand(
                "powershell", "-NoProfile", "-Command",
                "Expand-Archive -Force '$archive' '$installationDirectory'; " +
                    "Get-ChildItem -Path '$installationDirectory' -Recurse -Filter wasm-tools.exe | " +
                    "Select-Object -First 1 | Copy-Item -Destination '${wasmTools.asFile}'",
            )
        } else {
            runCommand("tar", "-xzf", archive.absolutePath, "-C", installationDirectory.absolutePath, "--strip-components=1")
        }

        if (!installedExecutable.isFile) {
            val downloadedExecutable = installationDirectory.walkTopDown().firstOrNull {
                it.isFile && it.name == "wasm-tools$executableSuffix"
            }
            checkNotNull(downloadedExecutable) { "wasm-tools archive did not contain wasm-tools$executableSuffix" }
            downloadedExecutable.copyTo(installedExecutable, overwrite = true)
        }
        installedExecutable.setExecutable(true)
    }
}

val apiSourcesJar = project(":api").tasks.named<Jar>("wasmWasiSourcesJar")
val unpackApiSources by tasks.registering(Sync::class) {
    group = "build setup"
    description = "Unpacks the published Pumpkin API source snapshot for component compilation."

    dependsOn(apiSourcesJar)
    from(apiSourcesJar.flatMap { it.archiveFile }.map { zipTree(it.asFile) })
    into(layout.buildDirectory.dir("generated/pumpkin-api"))
}
val witDirectory = unpackApiSources.map { it.destinationDir.resolve("wit/v0.1") }
val reactorAdapter = unpackApiSources.map { it.destinationDir.resolve("wasi/wasi_snapshot_preview1.reactor.wasm") }

kotlin {
    sourceSets.named("wasmWasiMain") {
        kotlin.srcDir(unpackApiSources)
    }
}

val compileReleaseWasm = tasks.named<BinaryenExec>("compileProductionExecutableKotlinWasmWasiOptimize")
val releaseCoreWasm = compileReleaseWasm.flatMap {
    it.outputDirectory.file("$projectWasmName.wasm")
}

val embedComponentWitRelease by tasks.registering(Exec::class) {
    group = "build"
    description = "Embeds Pumpkin's WIT into the release Kotlin/Wasm module."

    dependsOn(compileReleaseWasm, installWasmTools)
    inputs.dir(witDirectory)
    inputs.file(releaseCoreWasm)
    inputs.file(wasmTools)
    outputs.file(embeddedReleaseComponent)

    executable = wasmTools.asFile.absolutePath
    argumentProviders.add(
        object : CommandLineArgumentProvider {
            override fun asArguments() = listOf(
                "component",
                "embed",
                witDirectory.get().absolutePath,
                releaseCoreWasm.get().asFile.absolutePath,
                "-o", embeddedReleaseComponent.get().asFile.absolutePath,
            )
        }
    )

    doFirst {
        val outputDirectory = embeddedReleaseComponent.get().asFile.parentFile
        check(outputDirectory.mkdirs() || outputDirectory.isDirectory) {
            "Could not create component directory $outputDirectory"
        }
    }
}

val assemblePluginRelease by tasks.registering(Exec::class) {
    group = "build"
    description = "Creates the release WebAssembly component that Pumpkin can load."

    dependsOn(embedComponentWitRelease)
    inputs.file(embeddedReleaseComponent)
    inputs.file(reactorAdapter)
    inputs.file(wasmTools)
    outputs.file(releaseComponent)

    commandLine(
        wasmTools.asFile.absolutePath,
        "component",
        "new",
        embeddedReleaseComponent.get().asFile.absolutePath,
        "--adapt", "wasi_snapshot_preview1=${reactorAdapter.get().absolutePath}",
        "-o", releaseComponent.get().asFile.absolutePath,
    )
}

val validatePluginRelease by tasks.registering(Exec::class) {
    group = "verification"
    description = "Validates the release WebAssembly component."

    dependsOn(assemblePluginRelease)
    inputs.file(releaseComponent)
    inputs.file(wasmTools)

    commandLine(
        wasmTools.asFile.absolutePath,
        "validate",
        releaseComponent.get().asFile.absolutePath,
    )
}

tasks.named("assemble") {
    dependsOn(assemblePluginRelease)
}

tasks.named("check") {
    dependsOn(validatePluginRelease)
}

tasks.register("cleanProject") {
    group = "build"
    description = "Removes generated build output."
    dependsOn(tasks.named("clean"))
}
