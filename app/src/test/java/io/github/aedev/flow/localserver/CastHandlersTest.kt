package io.github.aedev.flow.localserver

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.player.dlna.DlnaDevice
import org.junit.Test

class CastHandlersTest {
    private val tv = DlnaDevice("Living \"room\" TV", "http://10.0.0.9/desc.xml", "uuid:1")

    @Test
    fun `the state lists devices by name and usn`() {
        assertThat(castStateJson(listOf(tv), discovering = true, casting = null))
            .isEqualTo("""{"discovering":true,"casting":null,"devices":[{"name":"Living \"room\" TV","usn":"uuid:1"}]}""")
    }

    @Test
    fun `the device being cast to is named by its usn`() {
        assertThat(castStateJson(listOf(tv), discovering = false, casting = tv)).contains("\"casting\":\"uuid:1\"")
    }

    @Test
    fun `no devices is an empty list`() {
        assertThat(castStateJson(emptyList(), discovering = false, casting = null)).endsWith("\"devices\":[]}")
    }
}
