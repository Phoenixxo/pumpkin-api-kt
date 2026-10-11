import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestReport

plugins {
    kotlin("jvm") version "2.4.0"
}

repositories { mavenCentral() }

dependencies {
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val helperSources = mapOf(
    "repeating-tasks" to listOf("PluginTasks.kt", "ScheduledTask.kt", "HandlerRegistry.kt"),
    "event-listeners" to listOf("PluginEvents.kt", "EventListener.kt", "HandlerRegistry.kt"),
    "commands" to listOf("PluginCommands.kt", "HandlerRegistry.kt"),
    "ai-goals" to listOf("AiGoal.kt", "PluginAiGoals.kt", "HandlerRegistry.kt"),
    "world-generators" to listOf("PluginWorldGenerators.kt", "HandlerRegistry.kt"),
    "resource-helpers" to listOf("InventoryExtensions.kt"),
    "lifecycle" to listOf("PluginCleanup.kt", "PumpkinPlugin.kt", "PluginTasks.kt", "ScheduledTask.kt", "HandlerRegistry.kt"),
)

// Binding doubles have identical public names but different instrumentation.
// Keep their classpaths separate within this build, rather than running seven builds.
val unitTests = helperSources.mapNotNull { (area, files) ->
    val directory = file("src/unit/$area/kotlin")
    if (!directory.isDirectory) return@mapNotNull null
    val suiteName = area.split('-').let { it.first() + it.drop(1).joinToString("") { word -> word.replaceFirstChar(Char::uppercaseChar) } }
    val sources = sourceSets.create(suiteName)
    configurations[sources.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
    configurations[sources.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())
    kotlin.sourceSets.named(suiteName) {
        kotlin.setSrcDirs(listOf(directory, "../api/src/wasmWasiMain/kotlin"))
        kotlin.include(files.map { "plugin/$it" })
        kotlin.include(fileTree(directory).matching { include("**/*.kt") }.files.map { it.relativeTo(directory).invariantSeparatorsPath })
    }
    tasks.register<Test>("${suiteName}Test") {
        group = "verification"
        description = "Runs the $area unit tests."
        testClassesDirs = sources.output.classesDirs
        classpath = sources.runtimeClasspath
        useJUnitPlatform()
    }
}

val unitTestReport = tasks.register<TestReport>("unitTestReport") {
    destinationDirectory.set(layout.buildDirectory.dir("reports/unit-tests"))
    testResults.from(unitTests)
}

tasks.register("unitTest") {
    group = "verification"
    description = "Runs all available Kotlin helper unit suites and writes their combined report."
    dependsOn(unitTests)
    finalizedBy(unitTestReport)
}

tasks.named<Test>("test") {
    dependsOn(unitTests)
    useJUnitPlatform()
    finalizedBy(unitTestReport)
}

tasks.named("check") { dependsOn("unitTest") }

if (findProject(":component") != null) {
    val integration = sourceSets.create("wasmIntegration")
    configurations[integration.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
    configurations[integration.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())
    kotlin.sourceSets.named(integration.name) {
        kotlin.setSrcDirs(listOf("src/wasmIntegration/kotlin"))
    }
    val serverExecutable = providers.gradleProperty("pumpkinServerExecutable")
        .orElse(providers.environmentVariable("PUMPKIN_SERVER_EXECUTABLE"))
    tasks.register<Test>("wasmIntegrationTest") {
        group = "verification"
        description = "Loads the built component into Pumpkin and verifies host callbacks and unload cleanup."
        dependsOn(":component:validatePluginRelease")
        testClassesDirs = integration.output.classesDirs
        classpath = integration.runtimeClasspath
        useJUnitPlatform()
        maxParallelForks = 1
        // A server boot is an integration check, even when its component did not change.
        outputs.upToDateWhen { false }
        doFirst {
            require(serverExecutable.isPresent) {
                "Set -PpumpkinServerExecutable=/absolute/path/to/pumpkin or PUMPKIN_SERVER_EXECUTABLE to run the Wasm host tests."
            }
            val executable = file(serverExecutable.get()).absoluteFile
            require(executable.isFile && executable.canExecute()) { "Pumpkin executable is missing or not executable: $executable" }
            systemProperty("pumpkin.serverExecutable", executable.absolutePath)
            systemProperty("pumpkin.component", project(":component").layout.buildDirectory.file("component.wasm").get().asFile.absolutePath)
            systemProperty("pumpkin.integrationDirectory", layout.buildDirectory.dir("wasm-integration").get().asFile.absolutePath)
        }
    }
    tasks.register("verifyAll") {
        group = "verification"
        description = "Runs unit tests, component validation and live Pumpkin integration tests."
        dependsOn("unitTest", "wasmIntegrationTest")
    }
}
