pluginManagement {
    val pluginVersion = providers.gradleProperty("pumpkinPluginVersion").getOrElse("0.1.1")
    plugins {
        id("org.pumpkinmc.plugin") version pluginVersion
    }
    repositories {
        val developmentRepository = providers.environmentVariable("PUMPKIN_CI_REPOSITORY").orNull
        if (developmentRepository != null) maven { url = uri(developmentRepository) }
        else mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "pumpkin-kotlin-tests"

// The component fixture belongs to the combined API test branch.
if (file("component/build.gradle.kts").isFile) include(":component")
