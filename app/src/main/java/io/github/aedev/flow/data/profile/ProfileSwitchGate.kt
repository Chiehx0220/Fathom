package io.github.aedev.flow.data.profile

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Closed while this process switches profile. Until it exits, nothing from outside the app (the system media
 * controls, a car, a widget) may start or browse the old profile's music. It only reopens if the switch fails.
 */
object ProfileSwitchGate {
    private val switching = MutableStateFlow<String?>(null)

    /** The profile being switched to while the gate is closed, for the screen that covers the app meanwhile. */
    val target: StateFlow<String?> = switching.asStateFlow()

    val isSwitching: Boolean get() = switching.value != null

    internal fun close(target: String) {
        switching.value = target
    }

    internal fun reopen() {
        switching.value = null
    }
}
