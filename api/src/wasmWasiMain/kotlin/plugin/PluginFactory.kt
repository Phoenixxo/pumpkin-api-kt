package plugin

/**
 * The consumer Gradle plugin replaces this factory with a direct reference to the plugin implementation.
 */
internal fun createPlugin(): PumpkinPlugin = error(
    "No plugin implementation is configured. Set pumpkin.pluginClass in build.gradle.kts.",
)
