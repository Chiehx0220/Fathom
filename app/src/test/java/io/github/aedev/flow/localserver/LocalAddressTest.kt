package io.github.aedev.flow.localserver

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocalAddressTest {
    @Test
    fun `wifi wins over mobile data and a vpn tunnel`() {
        val found = listOf("rmnet_data0" to "100.64.3.9", "tun0" to "10.8.0.2", "wlan0" to "192.168.1.20")

        assertThat(preferredLanAddress(found)).isEqualTo("192.168.1.20")
    }

    @Test
    fun `wifi wins over a hotspot when both are up`() {
        assertThat(preferredLanAddress(listOf("ap0" to "192.168.43.1", "wlan0" to "10.0.0.5"))).isEqualTo("10.0.0.5")
    }

    @Test
    fun `a hotspot or ethernet host is found when there is no wifi`() {
        assertThat(preferredLanAddress(listOf("rmnet0" to "100.64.3.9", "ap0" to "192.168.43.1"))).isEqualTo("192.168.43.1")
        assertThat(preferredLanAddress(listOf("eth0" to "192.168.1.8"))).isEqualTo("192.168.1.8")
    }

    @Test
    fun `an unknown interface is used only when nothing better exists`() {
        assertThat(preferredLanAddress(listOf("rmnet0" to "100.64.3.9", "bnep0" to "192.168.44.1"))).isEqualTo("192.168.44.1")
        assertThat(preferredLanAddress(listOf("rmnet0" to "100.64.3.9"))).isNull()
    }
}
