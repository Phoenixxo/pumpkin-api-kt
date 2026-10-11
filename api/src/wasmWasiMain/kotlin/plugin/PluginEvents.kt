package plugin

import pumpkin.Event
import pumpkin.Server

/**
 * Registers event listeners and routes their callbacks to your Kotlin code.
 *
 * There are no handler IDs to assign when using [listen]. Managed IDs start at
 * `0x8000_0000u` and are never reused. Direct WIT registrations must use lower IDs.
 *
 * Listeners release their Kotlin callbacks on close or plugin unload. This WIT
 * version has no host unregister operation, so closing a listener leaves its
 * host registration in place. See [EventListener.close] for that distinction.
 */
class PluginEvents internal constructor(
    private val handlers: HandlerRegistry<(Server.Server, Event.Event) -> Event.Event>,
    private val register: (PluginContext, UInt, Event.EventType, Event.EventPriority, Boolean) -> Unit =
        { context, id, type, priority, blocking -> context.registerEvent(id, type, priority, blocking) },
) {
    private var closed = false

    /**
     * Listens for [eventType] at the given [priority].
     *
     * Use [blocking] when the returned event should change or cancel the server
     * operation. A nonblocking listener can observe the event, but its return value
     * must not be relied on to change it.
     *
     * The server handle is closed when [handler] returns. Return the incoming event
     * when you have no changes to make. Resources in the returned event transfer
     * back to Pumpkin. If you discard event resources or throw, release the handles
     * you own.
     *
     * Registration makes the callback available before calling the host, so events
     * can arrive during registration. A failed registration releases the callback.
     *
     * @param context caller-owned plugin context used for registration
     * @param eventType event to listen for
     * @param priority listener ordering, defaulting to [Event.EventPriority.NORMAL]
     * @param blocking whether Pumpkin waits for this listener's event result
     * @param handler callback returning the event Pumpkin should receive
     * @return a handle that can stop this Kotlin callback
     * @throws IllegalStateException if the plugin has unloaded
     */
    fun listen(
        context: PluginContext,
        eventType: Event.EventType,
        priority: Event.EventPriority = Event.EventPriority.NORMAL,
        blocking: Boolean = false,
        handler: (Server.Server, Event.Event) -> Event.Event,
    ): EventListener {
        check(!closed) { "Cannot register event listeners after plugin unload." }
        val handlerId = handlers.add(handler)
        try {
            register(context, handlerId, eventType, priority, blocking)
        } catch (failure: Throwable) {
            handlers.remove(handlerId)
            throw failure
        }

        // Unload may have reentered during registration. Do not resurrect callbacks.
        if (closed) handlers.remove(handlerId)
        return EventListener { handlers.remove(handlerId) }
    }

    /**
     * Prevents new listeners and releases all locally registered callbacks.
     */
    internal fun close() {
        closed = true
        handlers.clear()
    }

    /**
     * Routes managed IDs to their listener and preserves direct WIT handler dispatch.
     *
     * Retired IDs return the incoming event unchanged. Managed and retired calls
     * close their server handle. Manual handlers keep responsibility for theirs.
     */
    internal fun dispatch(
        handlerId: UInt,
        server: Server.Server,
        event: Event.Event,
        manualHandler: (UInt, Server.Server, Event.Event) -> Event.Event,
    ): Event.Event {
        val handler = handlers[handlerId]
        return when {
            handler != null -> server.use { handler(it, event) }
            handlers.wasAllocated(handlerId) -> server.use { event }
            else -> manualHandler(handlerId, server, event)
        }
    }
}
