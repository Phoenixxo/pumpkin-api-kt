rootProject.name = "pumpkin-plugin-template-kt"

pluginManagement {
    resolutionStrategy {
        repositories {
            gradlePluginPortal()
        }
    }
}

include("api")
include("gradle-plugin")
