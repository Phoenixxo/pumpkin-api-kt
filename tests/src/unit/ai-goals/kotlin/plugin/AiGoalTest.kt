package plugin

import pumpkin.Server
import pumpkin.World

private class Goal : AiGoal {
    val calls = mutableListOf<String>()
    var callback: () -> Unit = {}
    override fun canStart(server: Server.Server, entity: World.Entity): Boolean {
        calls += "canStart"; callback(); return true
    }
    override fun shouldContinue(server: Server.Server, entity: World.Entity): Boolean {
        calls += "shouldContinue"; callback(); return true
    }
    override fun start(server: Server.Server, entity: World.Entity) { calls += "start"; callback() }
    override fun tick(server: Server.Server, entity: World.Entity) { calls += "tick"; callback() }
    override fun stop(server: Server.Server, entity: World.Entity) { calls += "stop"; callback() }
}

class AiGoalTest {
    @org.junit.jupiter.api.TestFactory
    fun lifecycleChecks(): List<org.junit.jupiter.api.DynamicTest> {
        val cases = mutableListOf<org.junit.jupiter.api.DynamicTest>()
        fun test(name: String, action: () -> Unit) {
            cases += org.junit.jupiter.api.DynamicTest.dynamicTest(name) { action() }
        }
        fun resources(action: (Server.Server, World.Entity) -> Unit) {
            val server = Server.Server()
            val entity = World.Entity()
            action(server, entity)
            check(server.closes == 1 && entity.closes == 1)
        }
        test("attach passes priority, retains caller mob, dispatches all callbacks") {
            var id = 0u
            val mob = World.Mob()
            val goals = PluginAiGoals(attachToHost = { actualMob, priority, actualId ->
                check(actualMob === mob && priority == 3u.toUByte()); id = actualId
            })
            val goal = Goal()
            goals.attach(mob, 3u, goal)
            check(mob.closes == 0)
            resources { server, entity -> check(goals.canStart(id, server, entity) { error("manual") }) }
            resources { server, entity -> check(goals.shouldContinue(id, server, entity) { error("manual") }) }
            resources { server, entity -> goals.start(id, server, entity) { error("manual") } }
            resources { server, entity -> goals.tick(id, server, entity) { error("manual") } }
            resources { server, entity -> goals.stop(id, server, entity) { error("manual") } }
            check(goal.calls == listOf("canStart", "shouldContinue", "start", "tick", "stop"))
        }
        test("disabled conditions are false and all action callbacks ignored") {
            var id = 0u
            var registrations = 0
            val goals = PluginAiGoals(attachToHost = { _, _, actualId -> id = actualId; registrations++ })
            val goal = Goal()
            val registration = goals.attach(World.Mob(), 0u, goal)
            registration.disable()
            registration.close()
            resources { server, entity -> check(!goals.canStart(id, server, entity) { error("manual") }) }
            resources { server, entity -> check(!goals.shouldContinue(id, server, entity) { error("manual") }) }
            resources { server, entity -> goals.start(id, server, entity) { error("manual") } }
            resources { server, entity -> goals.tick(id, server, entity) { error("manual") } }
            resources { server, entity -> goals.stop(id, server, entity) { error("manual") } }
            check(goal.calls.isEmpty() && registrations == 1)
        }
        test("unknown manual IDs preserve manual ownership") {
            val goals = PluginAiGoals()
            val server = Server.Server()
            val entity = World.Entity()
            var calls = 0
            check(goals.canStart(1u, server, entity) { calls++; true })
            goals.stop(1u, server, entity) { calls++ }
            check(calls == 2 && server.closes == 0 && entity.closes == 0)
        }
        test("registered before host attaches, safe with synchronous callbacks") {
            lateinit var goals: PluginAiGoals
            val goal = Goal()
            goals = PluginAiGoals(attachToHost = { _, _, id ->
                resources { server, entity -> check(goals.canStart(id, server, entity) { error("manual") }) }
            })
            goals.attach(World.Mob(), 0u, goal)
            check(goal.calls == listOf("canStart"))
        }
        test("host failure retires registration, ID never reused") {
            val ids = mutableListOf<UInt>()
            var fail = true
            val goals = PluginAiGoals(attachToHost = { _, _, id -> ids += id; if (fail) error("attach failed") })
            check(runCatching { goals.attach(World.Mob(), 0u, Goal()) }.isFailure)
            fail = false
            goals.attach(World.Mob(), 0u, Goal())
            check(ids[0] != ids[1])
            resources { server, entity -> check(!goals.canStart(ids[0], server, entity) { error("manual") }) }
        }
        test("unload during attach releases registration and rejects new ones") {
            lateinit var goals: PluginAiGoals
            var id = 0u
            goals = PluginAiGoals(attachToHost = { _, _, actualId -> id = actualId; goals.close() })
            goals.attach(World.Mob(), 0u, Goal()).disable()
            goals.close()
            resources { server, entity -> check(!goals.shouldContinue(id, server, entity) { error("manual") }) }
            check(runCatching { goals.attach(World.Mob(), 0u, Goal()) }.isFailure)
        }
        test("callback error stays primary when both resource closes fail") {
            var id = 0u
            val goals = PluginAiGoals(attachToHost = { _, _, actualId -> id = actualId })
            val failure = IllegalStateException("callback failed")
            val goal = Goal().apply { callback = { throw failure } }
            goals.attach(World.Mob(), 0u, goal)
            val server = Server.Server().apply { closeFailure = IllegalStateException("server close failed") }
            val entity = World.Entity().apply { closeFailure = IllegalStateException("entity close failed") }
            check(runCatching { goals.tick(id, server, entity) {} }.exceptionOrNull() === failure)
            check(server.closes == 1 && entity.closes == 1 && failure.suppressed.size == 2)
        }
        test("disable during callback affects subsequent dispatch") {
            var id = 0u
            val goals = PluginAiGoals(attachToHost = { _, _, actualId -> id = actualId })
            val goal = Goal()
            val registration = goals.attach(World.Mob(), 0u, goal)
            goal.callback = { registration.disable() }
            resources { server, entity -> goals.tick(id, server, entity) {} }
            resources { server, entity -> goals.tick(id, server, entity) { error("manual") } }
            check(goal.calls == listOf("tick"))
        }
        test("default continuation calls canStart") {
            var id = 0u
            var calls = 0
            val goals = PluginAiGoals(attachToHost = { _, _, actualId -> id = actualId })
            goals.attach(World.Mob(), 0u, object : AiGoal {
                override fun canStart(server: Server.Server, entity: World.Entity): Boolean { calls++; return false }
            })
            resources { server, entity -> check(!goals.shouldContinue(id, server, entity) { error("manual") }) }
            check(calls == 1)
        }
        test("disable during condition prevents start") {
            var id = 0u
            val goals = PluginAiGoals(attachToHost = { _, _, actualId -> id = actualId })
            val goal = Goal()
            val registration = goals.attach(World.Mob(), 0u, goal)
            goal.callback = { registration.disable() }
            resources { server, entity -> check(!goals.canStart(id, server, entity) { error("manual") }) }
        }
        return cases
    }
}
