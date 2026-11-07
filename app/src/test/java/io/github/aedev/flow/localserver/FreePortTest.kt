package io.github.aedev.flow.localserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket

class FreePortTest {
    @Test
    fun takesTheFirstPortThatStarts() {
        val tried = mutableListOf<Int>()
        val bound =
            startOnFreePort(first = 8080, span = 5, free = { true }) { port ->
                tried += port
                if (port < 8082) error("in use") else "ok"
            }
        assertEquals(8082, bound?.port)
        assertEquals(listOf(8080, 8081, 8082), tried)
    }

    @Test
    fun skipsPortsThatAreNotFreeWithoutStartingThem() {
        val tried = mutableListOf<Int>()
        val bound = startOnFreePort(first = 8080, span = 5, free = { it >= 8083 }) { port -> tried += port }
        assertEquals(8083, bound?.port)
        assertEquals(listOf(8083), tried)
    }

    @Test
    fun givesUpAfterTheLastPort() {
        assertNull(startOnFreePort(first = 9000, span = 3, free = { false }) { "never" })
    }

    @Test
    fun aPortHeldByAnotherSocketIsNotFree() {
        ServerSocket(0).use { taken ->
            assertFalse(canBind(taken.localPort))
        }
    }

    @Test
    fun aReleasedPortIsFree() {
        val port = ServerSocket(0).use { it.localPort }
        assertTrue(canBind(port))
    }
}
