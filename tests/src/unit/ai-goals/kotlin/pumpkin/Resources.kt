package pumpkin

open class Resource : AutoCloseable {
    var closes = 0
    var closeFailure: Throwable? = null
    override fun close() {
        closes++
        closeFailure?.let { throw it }
    }
}
object Server { class Server : Resource() }
object World {
    class Entity : Resource()
    class Mob : Resource() {
        fun addCustomAiGoal(priority: UByte, goalId: UInt): Unit = error("Unexpected host call")
    }
}
