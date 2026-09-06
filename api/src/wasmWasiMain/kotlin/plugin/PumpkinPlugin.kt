package plugin

import pumpkin.Command
import pumpkin.Context
import pumpkin.Event
import pumpkin.Metadata
import pumpkin.PluginRootFunctions
import pumpkin.Server
import pumpkin.World
import pumpkin.runtime.ComponentException

/** The implementation supplied by a Kotlin Pumpkin plugin. */
abstract class PumpkinPlugin {
    abstract fun metadata(): PluginMetadata

    open fun initPlugin() = Unit

    open fun onLoad(context: Context.Context): Result<Unit> = Result.success(Unit)

    open fun onUnload(context: Context.Context): Result<Unit> = Result.success(Unit)

    open fun handleCommand(
        commandId: UInt,
        sender: Command.CommandSender,
        server: Server.Server,
        args: Command.ConsumedArgs,
    ): Result<Int> = unsupportedCallback("handleCommand")

    open fun handleEvent(
        eventId: UInt,
        server: Server.Server,
        event: Event.Event,
    ): Event.Event = unsupportedCallback("handleEvent")

    open fun handleTask(handlerId: UInt, server: Server.Server) =
        unsupportedCallback<Unit>("handleTask")

    open fun handleIpcMessage(sender: String, message: List<UByte>): Result<List<UByte>> =
        Result.failure(ComponentException("This plugin cannot receive messages."))

    open fun handleAiGoalCanStart(goalId: UInt, server: Server.Server, entity: World.Entity): Boolean =
        unsupportedCallback("handleAiGoalCanStart")

    open fun handleAiGoalShouldContinue(goalId: UInt, server: Server.Server, entity: World.Entity): Boolean =
        unsupportedCallback("handleAiGoalShouldContinue")

    open fun handleAiGoalStart(goalId: UInt, server: Server.Server, entity: World.Entity) =
        unsupportedCallback<Unit>("handleAiGoalStart")

    open fun handleAiGoalTick(goalId: UInt, server: Server.Server, entity: World.Entity) =
        unsupportedCallback<Unit>("handleAiGoalTick")

    open fun handleAiGoalStop(goalId: UInt, server: Server.Server, entity: World.Entity) =
        unsupportedCallback<Unit>("handleAiGoalStop")
}

typealias PluginMetadata = Metadata.PluginMetadata

private var registeredPlugin: PumpkinPlugin? = null

/** Registers the plugin instance exported by this WebAssembly component. */
fun registerPlugin(plugin: PumpkinPlugin) {
    check(registeredPlugin == null) { "A Pumpkin plugin has already been registered." }
    registeredPlugin = plugin
}

private fun requirePlugin(): PumpkinPlugin = checkNotNull(registeredPlugin) {
    "No Pumpkin plugin has been registered. Call registerPlugin(...) during initialization."
}

private fun <T> unsupportedCallback(name: String): T =
    error("PumpkinPlugin.$name has not been implemented.")

/**
 * WIT export bridge used by the generated bindings. Plugin authors use [PumpkinPlugin] and
 * [registerPlugin] instead of implementing this generated interface directly.
 */
internal class PluginRootFunctionsExportsImpl {
    companion object : PluginRootFunctions.Exports {
        override fun initPlugin() = requirePlugin().initPlugin()

        override fun onLoad(context: Context.Context) = requirePlugin().onLoad(context)

        override fun onUnload(context: Context.Context) = requirePlugin().onUnload(context)

        override fun handleCommand(
            commandId: UInt,
            sender: Command.CommandSender,
            server: Server.Server,
            args: Command.ConsumedArgs,
        ) = requirePlugin().handleCommand(commandId, sender, server, args)

        override fun handleEvent(eventId: UInt, server: Server.Server, event: Event.Event) =
            requirePlugin().handleEvent(eventId, server, event)

        override fun handleTask(handlerId: UInt, server: Server.Server) =
            requirePlugin().handleTask(handlerId, server)

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
    }
}

/** WIT metadata export bridge used by the generated bindings. */
internal class MetadataImpl {
    companion object : Metadata {
        override fun getMetadata() = requirePlugin().metadata()
    }
}
