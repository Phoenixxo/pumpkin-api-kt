#!/usr/bin/env bash
set -euo pipefail

: "${PUMPKIN_CI_CONSUMER:?Set PUMPKIN_CI_CONSUMER to the temporary consumer directory}"
mkdir -p "$PUMPKIN_CI_CONSUMER/src/wasmWasiMain/kotlin/ci"

cat > "$PUMPKIN_CI_CONSUMER/settings.gradle.kts" <<'EOF'
pluginManagement {
    repositories {
        maven { url = uri(providers.environmentVariable("PUMPKIN_CI_REPOSITORY").get()) }
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "pumpkin-ci-consumer"
EOF

cat > "$PUMPKIN_CI_CONSUMER/build.gradle.kts" <<'EOF'
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform") version "2.4.0"
    id("io.github.pumpkin-mc.plugin") version "0.1.0-dev"
}

repositories {
    maven { url = uri(providers.environmentVariable("PUMPKIN_CI_REPOSITORY").get()) }
    mavenCentral()
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmWasi {
        nodejs()
        binaries.executable()
    }
}

pumpkin {
    apiVersion.set("0.1.0-dev")
    pluginClass.set("ci.SmokePlugin")
}
EOF

cat > "$PUMPKIN_CI_CONSUMER/gradle.properties" <<'EOF'
kotlin.daemon.jvmargs=-Xmx2g
EOF

cat > "$PUMPKIN_CI_CONSUMER/src/wasmWasiMain/kotlin/ci/SmokePlugin.kt" <<'EOF'
package ci

import plugin.PluginMetadata
import plugin.PumpkinPlugin

class SmokePlugin : PumpkinPlugin() {
    override fun metadata() = PluginMetadata(
        name = "ci-smoke-plugin",
        version = "0.1.0",
        authors = listOf("Pumpkin"),
        description = "Verifies consumer compilation with the generated plugin factory.",
        dependencies = emptyList(),
        permissions = emptyList(),
    )
}
EOF
