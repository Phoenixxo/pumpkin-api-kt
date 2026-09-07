plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
}

group = "io.github.pumpkin-mc"
version = providers.gradleProperty("pumpkinApiVersion").getOrElse("0.1.0-dev")

base {
    archivesName.set("pumpkin-api-kt-gradle-plugin")
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

tasks.processResources {
    from(rootProject.layout.projectDirectory.file("gradle/tool-versions.properties")) {
        into("io/github/pumpkinmc/gradle")
    }
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.0")
}

kotlin {
    jvmToolchain(17)
}

gradlePlugin {
    plugins {
        create("pumpkinPlugin") {
            id = "io.github.pumpkin-mc.plugin"
            implementationClass = "io.github.pumpkinmc.gradle.PumpkinPlugin"
            displayName = "Pumpkin Kotlin plugin build"
            description = "Builds Kotlin/Wasm Pumpkin plugins from the published API source snapshot."
        }
    }
}
