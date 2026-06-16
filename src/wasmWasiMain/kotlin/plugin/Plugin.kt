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
