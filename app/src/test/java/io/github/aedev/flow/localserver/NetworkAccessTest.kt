package io.github.aedev.flow.localserver

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.net.InetAddress

class NetworkAccessTest {
    private fun peer(address: String) = InetAddress.getByName(address)

    @Test
    fun `this device and its local network are served`() {
        for (address in listOf(
            "127.0.0.1",
            "::1",
            "192.168.1.5",
            "10.0.0.2",
            "172.16.0.9",
            "172.31.255.1",
            "169.254.10.3",
            "fe80::1",
            "fd7a:115c:a1e0::1",
            "100.64.0.1",
            "100.127.255.254",
            "::ffff:192.168.1.5",
        )) {
            assertThat(isLocalNetworkAddress(peer(address))).isTrue()
        }
    }

    @Test
    fun `a public peer is not served`() {
        for (address in listOf(
            "8.8.8.8",
            "172.32.0.1",
            "172.15.0.1",
            "100.63.255.255",
            "100.128.0.1",
            "2001:4860:4860::8888",
            "::ffff:8.8.8.8",
        )) {
            assertThat(isLocalNetworkAddress(peer(address))).isFalse()
        }
    }
}
