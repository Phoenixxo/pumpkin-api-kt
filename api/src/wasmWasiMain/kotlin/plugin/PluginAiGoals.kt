package plugin

import pumpkin.Server
import pumpkin.World

/**
 * Attaches custom [AiGoal] implementations to mobs and routes their callbacks.
 *
 * The API assigns IDs from `0x8000_0000u` upward and never reuses them. Direct WIT
 * goal registrations must use lower IDs.
 */
class PluginAiGoals internal constructor(
    private val handlers: HandlerRegistry<AiGoal> = HandlerRegistry(),
    private val attachToHost: (World.Mob, UByte, UInt) -> Unit = { mob, priority, id ->
        mob.addCustomAiGoal(priority, id)
    },
) {
    private var closed = false

    /**
     * Adds [goal] to [mob] at the given [priority].
     *
     * The mob handle remains yours. Keep the returned registration if the goal should
     * stop receiving Kotlin callbacks before plugin unload.
     *
     * Disabling a registration leaves the host's goal entry in place. This helper
     * never calls `clearAiGoals()`: that would remove every goal on the mob, including
     * ones this plugin did not add.
     *
     * @param mob mob receiving the custom goal
     * @param priority priority passed to Pumpkin's goal selector
     * @param goal condition and lifecycle implementation
     * @return a handle for disabling local callbacks
     * @throws IllegalStateException if the plugin has unloaded
     */
    fun attach(mob: World.Mob, priority: UByte, goal: AiGoal): AiGoalRegistration {
        check(!closed) { "Cannot attach AI goals after plugin unload" }
        val id = handlers.add(goal)
        try {
            attachToHost(mob, priority, id)
        } catch (failure: Throwable) {
            handlers.remove(id)
            throw failure
        }
        // The host may call back and unload the plugin while attaching the goal.
        if (closed) handlers.remove(id)
        return AiGoalRegistration { handlers.remove(id) }
    }

    internal fun canStart(id: UInt, server: Server.Server, entity: World.Entity, manual: () -> Boolean) =
        dispatch(id, server, entity, false, { it.canStart(server, entity) && handlers[id] === it }, manual)

    internal fun shouldContinue(id: UInt, server: Server.Server, entity: World.Entity, manual: () -> Boolean) =
        dispatch(id, server, entity, false, { it.shouldContinue(server, entity) && handlers[id] === it }, manual)

    internal fun start(id: UInt, server: Server.Server, entity: World.Entity, manual: () -> Unit) =
        dispatch(id, server, entity, Unit, { it.start(server, entity) }, manual)

    internal fun tick(id: UInt, server: Server.Server, entity: World.Entity, manual: () -> Unit) =
        dispatch(id, server, entity, Unit, { it.tick(server, entity) }, manual)

    internal fun stop(id: UInt, server: Server.Server, entity: World.Entity, manual: () -> Unit) =
        dispatch(id, server, entity, Unit, { it.stop(server, entity) }, manual)

    private fun <T> dispatch(
        id: UInt,
        server: Server.Server,
        entity: World.Entity,
        retired: T,
        callback: (AiGoal) -> T,
        manual: () -> T,
    ): T {
        val goal = handlers[id]
        if (goal == null && !handlers.wasAllocated(id)) return manual()
        // These exports transfer owned resource handles. Generated bindings do not drop them.
        return server.use { entity.use { if (goal == null) retired else callback(goal) } }
    }

    /**
     * Prevents new attachments and releases callbacks without clearing mob goals.
     */
    internal fun close() {
        closed = true
        handlers.clear()
    }
}
