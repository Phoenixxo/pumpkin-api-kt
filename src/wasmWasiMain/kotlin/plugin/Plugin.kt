package plugin;

import pumpkin.*

class PluginRootFunctionsExportsImpl {
    companion object : PluginRootFunctions.Exports {
        override fun initPlugin() {
            Logging.log(Logging.Level.INFO, "Init Kotlin plugin!")
        }

        override fun onLoad(context: Context.Context): Result<Unit> {
            Logging.log(Logging.Level.INFO, "Load Kotlin plugin!")
            return Result.success(Unit)
        }

        override fun onUnload(context: Context.Context): Result<Unit> {
            Logging.log(Logging.Level.INFO, "Unload Kotlin plugin!")
            return Result.success(Unit)
        }

        override fun handleCommand(
            commandId: UInt,
            sender: Command.CommandSender,
            server: Server.Server,
            args: Command.ConsumedArgs
        ): Result<Int> {
            TODO("Not yet implemented")
        }

        override fun handleEvent(eventId: UInt, server: Server.Server, event: Event.Event): Event.Event {
            TODO("Not yet implemented")
        }

        override fun handleTask(handlerId: UInt, server: Server.Server) {
            TODO("Not yet implemented")
        }

        override fun handleIpcMessage(sender: String, message: List<UByte>): Result<List<UByte>> {
            return Result.failure(
                pumpkin.runtime.ComponentException("This plugin cannot recieve messages.")
            );
        }

        override fun handleAiGoalCanStart(goalId: UInt, server: Server.Server, entity: World.Entity): Boolean {
            TODO("Not yet implemented");
        }

        override fun handleAiGoalShouldContinue(goalId: UInt, server: Server.Server, entity: World.Entity): Boolean {
            TODO("Not yet implemented");
        }

        override fun handleAiGoalStart(goalId: UInt, server: Server.Server, entity: World.Entity): Unit {
            TODO("Not yet implemented");
        }

        override fun handleAiGoalTick(goalId: UInt, server: Server.Server, entity: World.Entity): Unit {
            TODO("Not yet implemented");
        }

        override fun handleAiGoalStop(goalId: UInt, server: Server.Server, entity: World.Entity): Unit {
            TODO("Not yet implemented");
        }
    }
}

class MetadataImpl {
    companion object : Metadata {
        override fun getMetadata(): Metadata.PluginMetadata {
            return Metadata.PluginMetadata(
                name = "Example Kotlin Plugin",
                version = "0.1.0",
                authors = listOf("You"),
                description = "An example plugin written in Kotlin",
                dependencies = listOf(),
                permissions = listOf()
            );
        }
    }
}
