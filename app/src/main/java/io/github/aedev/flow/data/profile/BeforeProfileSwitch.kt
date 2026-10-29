package io.github.aedev.flow.data.profile

import java.util.concurrent.CopyOnWriteArraySet

/**
 * Work an open screen must finish before a switch restarts the process, such as grading the watch session of the
 * video on screen. The process dies with the screen still open, so nothing else would run it.
 */
object BeforeProfileSwitch {
    private val actions = CopyOnWriteArraySet<() -> Unit>()

    /** Registers [action] and returns what unregisters it, for the owner's teardown. */
    fun register(action: () -> Unit): () -> Unit {
        actions += action
        return { actions -= action }
    }

    internal fun run() = actions.forEach { it() }
}
