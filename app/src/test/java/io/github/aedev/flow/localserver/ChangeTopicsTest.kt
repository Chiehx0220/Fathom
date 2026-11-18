package io.github.aedev.flow.localserver

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ChangeTopicsTest {
    @Test
    fun `the value present at connect is not a change`() =
        runTest {
            val settings = MutableStateFlow<Any?>(1)
            changeTopics(listOf("settings" to settings), settleMs = 50).test {
                advanceTimeBy(500)
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a change names its topic once, bursts collapse`() =
        runTest {
            val settings = MutableStateFlow<Any?>(1)
            val subscriptions = MutableStateFlow<Any?>("a")
            changeTopics(listOf("settings" to settings, "subscriptions" to subscriptions), settleMs = 50).test {
                settings.value = 2
                advanceTimeBy(10)
                settings.value = 3
                advanceTimeBy(100)
                assertThat(awaitItem()).isEqualTo("settings")
                subscriptions.value = "b"
                advanceTimeBy(100)
                assertThat(awaitItem()).isEqualTo("subscriptions")
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an equal value is not a change`() =
        runTest {
            val settings = MutableStateFlow<Any?>(listOf(1, 2))
            changeTopics(listOf("settings" to settings), settleMs = 50).test {
                settings.value = listOf(1, 2)
                advanceTimeBy(200)
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }
}
