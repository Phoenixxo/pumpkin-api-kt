package plugin

/**
 * This handle represents a listener registered through [PluginEvents.listen].
 *
 * Keep the handle if the listener is only needed for part of your plugin's life.
 */
class EventListener internal constructor(private val closeAction: () -> Unit) {
    /**
     * Stops future calls to this Kotlin listener and releases its captured state.
     *
     * A callback already running can finish. Calling this more than once is safe.
     *
     * This does not unregister the listener from Pumpkin: the current WIT contract
     * has no operation for that. Until unload, incoming events for this ID are
     * returned unchanged without invoking your callback.
     */
    fun close() = closeAction()
}
