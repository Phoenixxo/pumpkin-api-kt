package plugin

import pumpkin.Server
import pumpkin.World

/**
 * Implement this interface to define the conditions and lifecycle callbacks for a custom mob goal.
 *
 * Implement [canStart], then override the callbacks your goal needs. Managed goal
 * callbacks close their server and entity handles on return. Use those handles
 * for the current call only.
 */
interface AiGoal {
    /**
     * Checks whether Pumpkin may start this goal.
     *
     * @return `true` when the goal is ready to run
     */
    fun canStart(server: Server.Server, entity: World.Entity): Boolean

    /**
     * Checks whether a running goal should keep running.
     *
     * The default checks [canStart] again. Override this when starting and continuing
     * need different conditions.
     *
     * @return `true` to keep the goal running
     */
    fun shouldContinue(server: Server.Server, entity: World.Entity): Boolean = canStart(server, entity)

    /**
     * Pumpkin calls this method when it starts this goal.
     *
     * Use this to set up state needed by [tick]. The default does nothing.
     */
    fun start(server: Server.Server, entity: World.Entity) = Unit

    /**
     * Pumpkin calls this method while the goal is running.
     *
     * Put the goal's ongoing behavior here. The default does nothing.
     */
    fun tick(server: Server.Server, entity: World.Entity) = Unit

    /**
     * Pumpkin calls this method when it stops the goal while its registration is enabled.
     *
     * Release per-run state here. Disabling the registration does not call [stop],
     * so perform any needed teardown before disabling it. The default does nothing.
     */
    fun stop(server: Server.Server, entity: World.Entity) = Unit
}

/**
 * This handle controls the Kotlin callbacks attached to a mob goal.
 *
 * Disabling the handle stops local dispatch. It does not remove the host's goal.
 */
class AiGoalRegistration internal constructor(private val disableAction: () -> Unit) : AutoCloseable {
    /**
     * Disables this goal's Kotlin callbacks and releases its captured state.
     *
     * Later condition checks return `false`, and start, tick and stop callbacks are
     * ignored. Repeated calls are safe.
     *
     * This does not invoke [AiGoal.stop]. Finish any per-run cleanup first. WIT has
     * no operation to remove one custom goal, so the host entry remains in place.
     */
    fun disable() = disableAction()

    /**
     * Disables the registration. Calling this method is equivalent to calling [disable].
     */
    override fun close() = disable()
}
