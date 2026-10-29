package io.github.aedev.flow.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * Whether the music service is alive in this process, for instance after the system media controls
 * resumed it. Kept apart from [Media3MusicService] so reading it never loads the service class.
 */
internal object MusicServiceState {
    private val state = MutableStateFlow(false)

    /** For waiting until the service has stopped, as a profile switch does. */
    val running: StateFlow<Boolean> = state.asStateFlow()

    private val cars = AtomicInteger()

    /** A car (Android Auto or Automotive) is connected to the music session. */
    val isCarConnected: Boolean get() = cars.get() > 0

    fun carConnected() {
        cars.incrementAndGet()
    }

    fun carDisconnected() {
        cars.updateAndGet { (it - 1).coerceAtLeast(0) }
    }

    var isRunning: Boolean
        get() = state.value
        set(value) {
            state.value = value
        }
}
