import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenExec

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

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

val cargoHome = providers.environmentVariable("CARGO_HOME")
    .orElse(providers.systemProperty("user.home").map { "$it/.cargo" })
val executableSuffix = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) ".exe" else ""
val cargoExecutable = cargoHome.map { "$it/bin/cargo$executableSuffix" }

val witDirectory = layout.projectDirectory.dir("wit/v0.1")
val reactorAdapter = layout.projectDirectory.file("wasi_snapshot_preview1.reactor.wasm")

val witBindgenRevision = "700f2db5e1d01f7bee8d756750c6f631171f520e"
val witBindgenDirectory = layout.projectDirectory.dir("tools/wit-bindgen/$witBindgenRevision")
val witBindgen = witBindgenDirectory.file("bin/wit-bindgen")

val wasmToolsVersion = "1.258.0"
val wasmToolsDirectory = layout.projectDirectory.dir("tools/wasm-tools/$wasmToolsVersion")
val wasmTools = wasmToolsDirectory.file("bin/wasm-tools$executableSuffix")

val generatedBindings = layout.buildDirectory.dir("generated/wit/wasmWasiMain/kotlin")
val projectWasmName = rootProject.name
val embeddedReleaseComponent = layout.buildDirectory.file(
    "intermediates/pumpkin-components/release/$projectWasmName-embedded.wasm"
)
val releaseComponent = layout.buildDirectory.file("$projectWasmName.wasm")

val installWitBindgen by tasks.registering(Exec::class) {
    group = "build setup"
    description = "Installs Pumpkin's pinned Kotlin binding generator."

    inputs.property("revision", witBindgenRevision)
    outputs.dir(witBindgenDirectory)

    commandLine(
        cargoExecutable.get(),
        "install",
        "wit-bindgen-cli",
        "--git", "https://github.com/Kotlin/wit-bindgen",
        "--rev", witBindgenRevision,
        "--locked",
        "--root", witBindgenDirectory.asFile.absolutePath,
    )
}

val installWasmTools by tasks.registering(Exec::class) {
    group = "build setup"
    description = "Installs the pinned wasm-tools executable."

    inputs.property("version", wasmToolsVersion)
    outputs.dir(wasmToolsDirectory)

    commandLine(
        cargoExecutable.get(),
        "install",
        "wasm-tools",
        "--version", wasmToolsVersion,
        "--locked",
        "--root", wasmToolsDirectory.asFile.absolutePath,
    )
}

val generateWitBindings by tasks.registering(Exec::class) {
    group = "build"
    description = "Generates Kotlin bindings from Pumpkin's WIT definitions."

    dependsOn(installWitBindgen)
    inputs.dir(witDirectory)
    inputs.file(witBindgen)
    inputs.property("kotlinPackage", "pumpkin")
    inputs.property("kotlinImports", "plugin.*")
    outputs.dir(generatedBindings)

    doFirst {
        val outputDirectory = generatedBindings.get().asFile
        check(outputDirectory.deleteRecursively() || !outputDirectory.exists()) {
            "Could not remove stale bindings from $outputDirectory"
        }
        check(outputDirectory.mkdirs() || outputDirectory.isDirectory) {
            "Could not create bindings directory $outputDirectory"
        }
    }

    commandLine(
        witBindgen.asFile.absolutePath,
        "kotlin",
        "--kotlin-imports", "plugin.*",
        "--kotlin-package-name", "pumpkin",
        witDirectory.asFile.absolutePath,
        "--out-dir", generatedBindings.get().asFile.absolutePath,
    )
}

kotlin {
    sourceSets.named("wasmWasiMain") {
        kotlin.srcDir(generateWitBindings)
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
                witDirectory.asFile.absolutePath,
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
        "--adapt", "wasi_snapshot_preview1=${reactorAdapter.asFile.absolutePath}",
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
