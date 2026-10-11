package plugin

import kotlin.test.*
import pumpkin.Context
import pumpkin.Event
import pumpkin.Server

class EventListenerTest {
    private val context = Context.Context()
    private val server = Server.Server()
    private val incoming = Event.Event(1)
    private val updated = Event.Event(2)
    private val type = Event.EventType.PLAYER_JOIN_EVENT

    private fun dispatch(events: PluginEvents, id: UInt): Event.Event =
        events.dispatch(id, server, incoming) { _, _, _ -> error("Unexpected manual dispatch") }

    @Test fun registrationCanDispatchSynchronouslyAndForwardsOptions() {
        lateinit var events: PluginEvents
        events = PluginEvents(HandlerRegistry()) { actualContext, id, actualType, priority, blocking ->
            assertSame(context, actualContext)
            assertEquals(type, actualType)
            assertEquals(Event.EventPriority.HIGH, priority)
            assertTrue(blocking)
            assertSame(updated, dispatch(events, id))
        }
        events.listen(context, type, Event.EventPriority.HIGH, true) { actualServer, event ->
            assertSame(server, actualServer)
            assertSame(incoming, event)
            updated
        }
    }

    @Test fun closingRetiresIdsAndDoesNotFallBackToManualCallbacks() {
        val ids = mutableListOf<UInt>()
        val events = PluginEvents(HandlerRegistry()) { _, id, _, _, _ -> ids.add(id) }
        val listener = events.listen(context, type) { _, _ -> updated }
        assertSame(updated, dispatch(events, ids[0]))
        listener.close()
        listener.close()
        assertSame(incoming, dispatch(events, ids[0]))
        events.listen(context, type) { _, _ -> updated }
        assertNotEquals(ids[0], ids[1])
        assertSame(incoming, dispatch(events, ids[0]))
        assertSame(updated, dispatch(events, ids[1]))
    }

    @Test fun registrationFailureRetiresTheCallback() {
        var id = 0u
        val failure = IllegalStateException("Host registration failed")
        val events = PluginEvents(HandlerRegistry()) { _, registeredId, _, _, _ ->
            id = registeredId
            throw failure
        }
        assertSame(failure, assertFailsWith<IllegalStateException> {
            events.listen(context, type) { _, _ -> updated }
        })
        assertSame(incoming, dispatch(events, id))
    }

    @Test fun unloadDuringRegistrationCannotResurrectCallbacks() {
        var id = 0u
        lateinit var events: PluginEvents
        events = PluginEvents(HandlerRegistry()) { _, registeredId, _, _, _ ->
            id = registeredId
            events.close()
        }
        val listener = events.listen(context, type) { _, _ -> updated }
        listener.close()
        assertSame(incoming, dispatch(events, id))
        assertFailsWith<IllegalStateException> { events.listen(context, type) { _, event -> event } }
    }

    @Test fun closingAllListenersIsIdempotentAndPreservesManualDispatch() {
        var id = 0u
        val events = PluginEvents(HandlerRegistry()) { _, registeredId, _, _, _ -> id = registeredId }
        events.listen(context, type) { _, _ -> updated }
        events.close()
        events.close()
        assertSame(incoming, dispatch(events, id))
        assertSame(updated, events.dispatch(7u, server, incoming) { manualId, actualServer, event ->
            assertEquals(7u, manualId)
            assertSame(server, actualServer)
            assertSame(incoming, event)
            updated
        })
    }

    @Test fun listenerMayCloseItselfDuringDispatch() {
        var id = 0u
        val events = PluginEvents(HandlerRegistry()) { _, registeredId, _, _, _ -> id = registeredId }
        lateinit var listener: EventListener
        listener = events.listen(context, type) { _, _ ->
            listener.close()
            assertSame(incoming, dispatch(events, id))
            updated
        }
        assertSame(updated, dispatch(events, id))
        assertSame(incoming, dispatch(events, id))
    }

    @Test fun thrownCallbackDoesNotRemovePersistentListener() {
        var id = 0u
        var calls = 0
        val failure = IllegalStateException("Callback failed")
        val events = PluginEvents(HandlerRegistry()) { _, registeredId, _, priority, blocking ->
            id = registeredId
            assertEquals(Event.EventPriority.NORMAL, priority)
            assertFalse(blocking)
        }
        val listener = events.listen(context, type) { _, _ ->
            calls++
            throw failure
        }
        repeat(2) {
            assertSame(failure, assertFailsWith<IllegalStateException> { dispatch(events, id) })
        }
        assertEquals(2, calls)
        assertEquals(2, server.closes)
        listener.close()
        assertSame(incoming, dispatch(events, id))
        assertEquals(3, server.closes)
    }

    @Test fun managedDispatchClosesServerAndManualDispatchRetainsOwnership() {
        var id = 0u
        val events = PluginEvents(HandlerRegistry()) { _, registeredId, _, _, _ -> id = registeredId }
        events.listen(context, type) { _, event -> event }
        dispatch(events, id)
        assertEquals(1, server.closes)
        events.dispatch(3u, server, incoming) { _, _, event -> event }
        assertEquals(1, server.closes)
    }
}
