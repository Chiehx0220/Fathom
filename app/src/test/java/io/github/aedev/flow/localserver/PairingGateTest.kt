package io.github.aedev.flow.localserver

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PairingGateTest {
    @Test
    fun `an unpaired browser gets the pairing page at the address and nothing else`() {
        assertThat(gateFor("/", paired = false, loopback = false)).isEqualTo(Gate.PAIR_PAGE)
        assertThat(gateFor("/api/v1/history", paired = false, loopback = false)).isEqualTo(Gate.REFUSE)
        assertThat(gateFor("/stream", paired = false, loopback = false)).isEqualTo(Gate.REFUSE)
        assertThat(gateFor(PAIR_PATH, paired = false, loopback = false)).isEqualTo(Gate.ADMIT)
    }

    @Test
    fun `a paired browser and the phone itself pass`() {
        assertThat(gateFor("/api/v1/history", paired = true, loopback = false)).isEqualTo(Gate.ADMIT)
        assertThat(gateFor("/api/v1/history", paired = false, loopback = true)).isEqualTo(Gate.ADMIT)
    }
}
