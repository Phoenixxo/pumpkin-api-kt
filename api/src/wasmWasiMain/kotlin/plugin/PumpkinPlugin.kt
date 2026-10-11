package plugin

import pumpkin.Command
import pumpkin.Context
import pumpkin.Event
import pumpkin.Metadata
import pumpkin.PluginRootFunctions
import pumpkin.Server
import pumpkin.World
import pumpkin.runtime.ComponentException

/** This type represents the context passed to plugin lifecycle callbacks. */
typealias PluginContext = Context.Context

/**
 * Extend this class to implement a Kotlin Pumpkin plugin.
 *
 * Provide [metadata] and use [onLoad] for registration and setup. Override the
 * callbacks your plugin needs. Optional manual handlers fail if invoked without
 * an implementation. [tasks] manages scheduling and callback IDs for you.
 */
abstract class PumpkinPlugin {

    internal val taskHandlers = HandlerRegistry<(Server.Server) -> Unit>()
    internal val eventHandlers = HandlerRegistry<(Server.Server, Event.Event) -> Event.Event>()

    /** This helper dispatches event callbacks automatically and releases them when this plugin unloads. */
    val events = PluginEvents(eventHandlers)

    /** Closed listeners return the incoming event unchanged. Manual IDs retain their callback. */
    internal fun dispatchEvent(eventId: UInt, server: Server.Server, event: Event.Event): Event.Event =
        events.dispatch(eventId, server, event, ::handleEvent)

    /**
     * Use this scheduler to manage callbacks owned by your plugin.
     *
     * Tasks scheduled through this API are dispatched automatically and cleaned up
     * when they complete, are cancelled, or the plugin is unloaded.
     *
     * This scheduler reserves handler IDs from `0x8000_0000u` onward. Manual task
     * handlers must use IDs below that value.
     */
    val tasks = PluginTasks(taskHandlers)

    /** Dispatches managed tasks, ignores retired IDs, and falls back to manual handlers. */
    internal fun dispatchTask(handlerId: UInt, server: Server.Server) {
        val handler = taskHandlers[handlerId]

        when {
            handler != null -> handler(server)
            taskHandlers.wasAllocated(handlerId) -> Unit
            else -> handleTask(handlerId, server)
        }
    }

    /**
     * Returns this plugin's name, version and other metadata.
     *
     * Pumpkin may request metadata before [onLoad]. Keep this method independent
     * of resources created during loading.
     *
     * @return metadata describing this plugin
     */
    abstract fun metadata(): PluginMetadata

    /**
     * Pumpkin calls this method once when it initializes the component.
     *
     * Use [onLoad] for setup that needs a plugin context. The default does nothing.
     */
    open fun initPlugin() = Unit

    /**
     * Pumpkin calls this method when it loads the plugin.
     *
     * Register callbacks and prepare plugin state here. The default succeeds without
     * performing any setup.
     *
     * @param context context supplied for plugin registration and server access
     * @return success when setup completes, or a failure describing why loading failed
     */
    open fun onLoad(context: PluginContext): Result<Unit> = Result.success(Unit)

    /**
     * Pumpkin calls this method when it unloads the plugin.
     *
     * Finish saving state or releasing plugin-owned resources here. Managed cleanup
     * runs after this callback, even if it fails. The default succeeds.
     *
     * @param context context supplied for unloading
     * @return success when teardown completes, or a failure describing the problem
     */
    open fun onUnload(context: PluginContext): Result<Unit> = Result.success(Unit)

    /**
     * Handles a command registered directly through the WIT bindings.
     *
     * Use IDs below `0x8000_0000u` for manual registrations. This callback owns its
     * sender, server and argument handles. Release them when finished.
     *
     * @param commandId handler ID assigned by the registration
     * @param sender command sender
     * @param server server handling the command
     * @param args parsed arguments
     * @return the command's success count or a WIT-compatible command failure
     */
    open fun handleCommand(
        commandId: UInt,
        sender: Command.CommandSender,
        server: Server.Server,
        args: Command.ConsumedArgs,
    ): Result<Int> = unsupportedCallback("handleCommand")

    /**
     * Supplies completions for a command registered directly through WIT.
     *
     * Manual IDs must be below `0x8000_0000u`. Release the sender and server handles
     * when finished. Resources in returned suggestions transfer to Pumpkin.
     *
     * @param handlerId suggestion handler ID assigned by the registration
     * @param sender sender requesting completions
     * @param server server handling the request
     * @param request input and argument range being completed
     * @return completions for the request's argument range
     */
    open fun handleCommandSuggestion(
        handlerId: UInt,
        sender: Command.CommandSender,
        server: Server.Server,
        request: Command.SuggestionRequest,
    ): Command.CommandSuggestions = unsupportedCallback("handleCommandSuggestion")

    /**
     * Handles an event registered directly through the WIT bindings.
     *
     * Manual IDs must be below `0x8000_0000u`. Return the incoming event when no
     * changes are needed. The returned event's resources transfer to Pumpkin.
     * Release the server handle and any event resources you discard.
     *
     * @param eventId handler ID assigned by the registration
     * @param server server dispatching the event
     * @param event incoming event
     * @return the event to pass back to Pumpkin
     */
    open fun handleEvent(
        eventId: UInt,
        server: Server.Server,
        event: Event.Event,
    ): Event.Event = unsupportedCallback("handleEvent")

    /**
     * Runs a task registered directly through the scheduler bindings.
     *
     * Prefer [tasks] for automatic callback routing and cancellation. Direct
     * registrations must use IDs below `0x8000_0000u`. Higher IDs belong to managed
     * tasks. This manual callback owns the server handle and must release it.
     *
     * @param handlerId ID supplied when scheduling the task
     * @param server server executing the task
     */
    open fun handleTask(handlerId: UInt, server: Server.Server) =
        unsupportedCallback<Unit>("handleTask")

    /**
     * Receives a message from another plugin and returns a reply.
     *
     * The default returns a failure indicating that this plugin does not receive
     * messages. Use a string-valued [ComponentException] for WIT-compatible failures.
     *
     * @param sender ID of the sending plugin
     * @param message message bytes
     * @return reply bytes, or a failure describing why the message could not be handled
     */
    open fun handleIpcMessage(sender: String, message: List<UByte>): Result<List<UByte>> =
        Result.failure(ComponentException("This plugin cannot receive messages."))

    /**
     * Checks whether a directly registered custom goal may start.
     *
     * Manual goal IDs must be below `0x8000_0000u`. This callback owns the server
     * and entity handles and must release them.
     *
     * @return `true` when the goal may start
     */
    open fun handleAiGoalCanStart(goalId: UInt, server: Server.Server, entity: World.Entity): Boolean =
        unsupportedCallback("handleAiGoalCanStart")

    /**
     * Checks whether a directly registered custom goal should continue.
     *
     * This manual callback owns the server and entity handles and must release them.
     *
     * @return `true` to keep the goal running
     */
    open fun handleAiGoalShouldContinue(goalId: UInt, server: Server.Server, entity: World.Entity): Boolean =
        unsupportedCallback("handleAiGoalShouldContinue")

    /**
     * Starts a directly registered custom goal.
     *
     * This manual callback owns the server and entity handles and must release them.
     */
    open fun handleAiGoalStart(goalId: UInt, server: Server.Server, entity: World.Entity) =
        unsupportedCallback<Unit>("handleAiGoalStart")

    /**
     * Updates a directly registered custom goal while it runs.
     *
     * This manual callback owns the server and entity handles and must release them.
     */
    open fun handleAiGoalTick(goalId: UInt, server: Server.Server, entity: World.Entity) =
        unsupportedCallback<Unit>("handleAiGoalTick")

    /**
     * Stops a directly registered custom goal.
     *
     * Release the goal's per-run state and the owned server and entity handles.
     */
    open fun handleAiGoalStop(goalId: UInt, server: Server.Server, entity: World.Entity) =
        unsupportedCallback<Unit>("handleAiGoalStop")

    /**
     * Generates a phase for a directly registered world generator.
     *
     * Manual generator IDs must be below `0x8000_0000u`. This callback owns the chunk
     * handle and must release it when finished. Custom generators replace the world's
     * terrain generator. Skipped phases do not receive automatic default generation.
     *
     * @param generatorId ID supplied when installing the generator
     * @param phase phase requested by Pumpkin
     * @param chunk buffer to update for this phase
     */
    open fun handleGeneratePhase(
        generatorId: UInt,
        phase: World.GenerationPhase,
        chunk: World.ChunkBuffer,
    ) = unsupportedCallback<Unit>("handleGeneratePhase")
}

typealias PluginMetadata = Metadata.PluginMetadata

private var pluginInstance: PumpkinPlugin? = null

private fun requirePlugin(): PumpkinPlugin = pluginInstance ?: createPlugin().also {
    pluginInstance = it
}

private fun <T> unsupportedCallback(name: String): T =
    error("PumpkinPlugin.$name has not been implemented.")

/**
 * This bridge implements the WIT exports used by the generated bindings. Plugin authors extend [PumpkinPlugin]
 * and configure pumpkin.pluginClass.
 * 
 * The generated bootstrap supplies the instance automatically.
 */
internal class PluginRootFunctionsExportsImpl {

    companion object : PluginRootFunctions.Exports {
        override fun initPlugin() = requirePlugin().initPlugin()

        override fun onLoad(context: PluginContext) = requirePlugin().onLoad(context)

        override fun onUnload(context: PluginContext): Result<Unit> {
            val plugin = requirePlugin()

            val unloaded = try {
                plugin.onUnload(context)
            } catch (error: Throwable) {
                Result.failure(error)
            }

            plugin.events.close()
            val cleaned = plugin.tasks.close()

            val unloadError = unloaded.exceptionOrNull()
            val cleanupError = cleaned.exceptionOrNull()

            if (unloadError != null && cleanupError != null
                && unloadError !== cleanupError
                ) {
                unloadError.addSuppressed(cleanupError)
            }

            return if (unloaded.isFailure) unloaded else cleaned

        }

        override fun handleCommand(
            commandId: UInt,
            sender: Command.CommandSender,
            server: Server.Server,
            args: Command.ConsumedArgs,
        ) = requirePlugin().handleCommand(commandId, sender, server, args)

        override fun handleEvent(eventId: UInt, server: Server.Server, event: Event.Event) =
            requirePlugin().dispatchEvent(eventId, server, event)

        override fun handleCommandSuggestion(
            handlerId: UInt,
            sender: Command.CommandSender,
            server: Server.Server,
            request: Command.SuggestionRequest,
        ) = requirePlugin().handleCommandSuggestion(handlerId, sender, server, request)

        override fun handleTask(handlerId: UInt, server: Server.Server) =
            requirePlugin().dispatchTask(handlerId, server)

        override fun handleIpcMessage(sender: String, message: List<UByte>) =
            requirePlugin().handleIpcMessage(sender, message)

        override fun handleAiGoalCanStart(goalId: UInt, server: Server.Server, entity: World.Entity) =
            requirePlugin().handleAiGoalCanStart(goalId, server, entity)

        override fun handleAiGoalShouldContinue(goalId: UInt, server: Server.Server, entity: World.Entity) =
            requirePlugin().handleAiGoalShouldContinue(goalId, server, entity)

        override fun handleAiGoalStart(goalId: UInt, server: Server.Server, entity: World.Entity) =
            requirePlugin().handleAiGoalStart(goalId, server, entity)

        override fun handleAiGoalTick(goalId: UInt, server: Server.Server, entity: World.Entity) =
            requirePlugin().handleAiGoalTick(goalId, server, entity)

        override fun handleAiGoalStop(goalId: UInt, server: Server.Server, entity: World.Entity) =
            requirePlugin().handleAiGoalStop(goalId, server, entity)

        override fun handleGeneratePhase(
            generatorId: UInt,
            phase: World.GenerationPhase,
            chunk: World.ChunkBuffer,
        ) = requirePlugin().handleGeneratePhase(generatorId, phase, chunk)
    }
}

/** This bridge implements the WIT metadata export used by the generated bindings. */
internal class MetadataImpl {
    companion object : Metadata {
        override fun getMetadata() = requirePlugin().metadata()
    }
}
