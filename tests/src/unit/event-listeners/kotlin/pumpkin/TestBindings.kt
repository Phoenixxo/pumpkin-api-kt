package pumpkin

// Contract doubles keep these tests independent of Wasm imports and server resources.
object Server {
    class Server : AutoCloseable {
        var closes = 0
        override fun close() { closes++ }
    }
}
object Event {
    class Event(val value: Int)
    enum class EventType { PLAYER_JOIN_EVENT }
    enum class EventPriority { HIGHEST, HIGH, NORMAL, LOW, LOWEST }
}
object Context {
    class Context {
        fun registerEvent(id: UInt, type: Event.EventType, priority: Event.EventPriority, blocking: Boolean) = Unit
    }
}
