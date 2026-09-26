package io.github.aedev.flow.bilibili

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

/**
 * The av/bv codec and the mixin-key shuffle are pinned to the values documented in
 * SocialSisterYi/bilibili-API-collect. If one fails after a Bilibili change, the constant tables in
 * [BilibiliSigning] are what to diff against PipePipeExtractor's utils.java.
 */
class BilibiliSigningTest {
    @Test
    fun `av170001 encodes to the well known bvid and back`() {
        assertThat(BilibiliSigning.av2bv(170001L)).isEqualTo("BV17x411w7KC")
        assertThat(BilibiliSigning.bv2av("BV17x411w7KC")).isEqualTo(170001L)
    }

    @Test
    fun `bv2av inverts av2bv across a spread of ids`() {
        for (aid in longArrayOf(1L, 2L, 12345L, 170001L, 999_999_999L, 1_500_000_000_000L)) {
            assertThat(BilibiliSigning.bv2av(BilibiliSigning.av2bv(aid))).isEqualTo(aid)
        }
    }

    @Test
    fun `mixin key matches the documented example`() {
        val key =
            BilibiliSigning.mixinKey(
                "7cd084941338484aae1ad9425b84077c",
                "4932caff0ff746eab6f01bf08b70ac45",
            )
        assertThat(key).isEqualTo("ea1db124af3c7062474693fa704f4ff8")
    }

    @Test
    fun `signing adds a 32 hex w_rid and the wts and is stable for the same inputs`() {
        val key = "ea1db124af3c7062474693fa704f4ff8"

        fun sign(): String {
            val params = linkedMapOf("foo" to "114", "bar" to "514", "zab" to "1919810")
            return BilibiliSigning.signWbi(LinkedHashMap(params), key, 1684746387L)
        }

        val query = sign()
        assertThat(query).contains("wts=1684746387")
        assertThat(Regex("w_rid=[0-9a-f]{32}").containsMatchIn(query)).isTrue()
        assertThat(query.startsWith("foo=114&bar=514&zab=1919810&")).isTrue()
        assertThat(sign()).isEqualTo(query)
    }

    @Test
    fun `signing hashes over the sorted params so insertion order does not change w_rid`() {
        val key = "ea1db124af3c7062474693fa704f4ff8"
        val a = BilibiliSigning.signWbi(linkedMapOf("b" to "2", "a" to "1"), key, 100L)
        val b = BilibiliSigning.signWbi(linkedMapOf("a" to "1", "b" to "2"), key, 100L)
        assertThat(a.substringAfter("w_rid=").take(32)).isEqualTo(b.substringAfter("w_rid=").take(32))
    }

    @Test
    fun `dm_img params carry the four fields the web player sends`() {
        val device = DeviceForger.forgeDevice(Random(7))
        val params = BilibiliSigning.dmImgParams(device, Random(7))
        assertThat(params.keys).containsExactly("dm_img_list", "dm_img_str", "dm_cover_img_str", "dm_img_inter").inOrder()
        assertThat(params["dm_img_list"]).isEqualTo("[]")
        assertThat(params["dm_img_inter"]).startsWith("{\"ds\":[],\"wh\":[")
    }

    @Test
    fun `forged devices are modern windows chrome and their webgl strings drop base64 padding`() {
        val device = DeviceForger.forgeDevice(Random(1))
        assertThat(device.userAgent).contains("Windows NT 10.0; Win64; x64")
        assertThat(Regex("Chrome/13[0-9][.]0[.]0[.]0").containsMatchIn(device.userAgent)).isTrue()
        assertThat(device.webGlVersionBase64.endsWith("=")).isFalse()
        assertThat(device.webGlRendererInfoBase64.endsWith("=")).isFalse()
    }
}
