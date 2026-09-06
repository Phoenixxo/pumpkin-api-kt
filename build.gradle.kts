import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

import org.gradle.process.CommandLineArgumentProvider
import org.gradle.api.tasks.Sync
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenExec

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

val projectWasmName = rootProject.name
val embeddedReleaseComponent = layout.buildDirectory.file(
    "intermediates/pumpkin-components/release/$projectWasmName-embedded.wasm"
)
val releaseComponent = layout.buildDirectory.file("$projectWasmName.wasm")

val installWasmTools by tasks.registering(Exec::class) {
    group = "build setup"
    description = "Installs the pinned wasm-tools executable."

    inputs.property("version", wasmToolsVersion)
    outputs.dir(wasmToolsDirectory)

    commandLine(
        providers.environmentVariable("CARGO_HOME")
            .orElse(providers.systemProperty("user.home").map { "$it/.cargo" })
            .map { "$it/bin/cargo$executableSuffix" }
            .get(),
        "install",
        "wasm-tools",
        "--version", wasmToolsVersion,
        "--locked",
        "--root", wasmToolsDirectory.asFile.absolutePath,
    )
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
