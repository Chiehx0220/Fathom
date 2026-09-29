package io.github.aedev.flow.bilibili

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.DeflaterOutputStream

class BilibiliLivePacketTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val chat =
        """{"cmd":"DANMU_MSG","info":[[0,1,25,16711680,1710000000000,0,0,"x",0,0,0,"",0,"{}","{}"],"hello",[1,"u",0]]}"""
    private val topChat = """{"cmd":"DANMU_MSG:4:0:2:2:2:0","info":[[0,5,25,255,0],"pinned"]}"""
    private val superChat =
        """{"cmd":"SUPER_CHAT_MESSAGE","data":{"price":30,"message":"thanks","background_bottom_color":"#2A60B2"}}"""

    /** A packet the way the server wraps a batch: version 2, holding zlib-compressed packets. */
    private fun compressed(vararg bodies: String): ByteArray {
        val inner = bodies.map { BilibiliLivePacket.encode(BilibiliLivePacket.OP_MESSAGE, it) }.reduce { a, b -> a + b }
        val zipped = ByteArrayOutputStream().also { out -> DeflaterOutputStream(out).use { it.write(inner) } }.toByteArray()
        return ByteBuffer
            .allocate(16 + zipped.size)
            .putInt(16 + zipped.size)
            .putShort(16)
            .putShort(2)
            .putInt(BilibiliLivePacket.OP_MESSAGE)
            .putInt(1)
            .put(zipped)
            .array()
    }

    @Test
    fun `an encoded packet decodes back to its operation and body`() {
        val packets = BilibiliLivePacket.decode(BilibiliLivePacket.encode(BilibiliLivePacket.OP_AUTH, "{}"))

        assertThat(packets).hasSize(1)
        assertThat(packets[0].op).isEqualTo(BilibiliLivePacket.OP_AUTH)
        assertThat(packets[0].body.toString(Charsets.UTF_8)).isEqualTo("{}")
    }

    @Test
    fun `packets batched in one compressed packet are all read`() {
        val messages = BilibiliLivePacket.messages(compressed(chat, superChat), json)

        assertThat(messages).hasSize(2)
        assertThat(messages[0]).isInstanceOf(BilibiliLiveMessage.Chat::class.java)
        assertThat(messages[1]).isInstanceOf(BilibiliLiveMessage.SuperChat::class.java)
    }

    @Test
    fun `a chat message carries its text, colour and position`() {
        val red = BilibiliLivePacket.parse(chat, json) as BilibiliLiveMessage.Chat
        assertThat(red.text).isEqualTo("hello")
        assertThat(red.argbColor).isEqualTo(0xFFFF0000.toInt())
        assertThat(red.position).isEqualTo(BilibiliDanmakuPosition.SCROLL)

        val top = BilibiliLivePacket.parse(topChat, json) as BilibiliLiveMessage.Chat
        assertThat(top.position).isEqualTo(BilibiliDanmakuPosition.TOP)
    }

    @Test
    fun `a super chat carries its price and background colour`() {
        val message = BilibiliLivePacket.parse(superChat, json) as BilibiliLiveMessage.SuperChat

        assertThat(message.text).isEqualTo("(¥30) thanks")
        assertThat(message.priceYuan).isEqualTo(30)
        assertThat(message.argbColor).isEqualTo(0xFF2A60B2.toInt())
    }

    @Test
    fun `other commands and malformed bodies produce nothing`() {
        assertThat(BilibiliLivePacket.parse("""{"cmd":"WATCHED_CHANGE","data":{}}""", json)).isNull()
        assertThat(BilibiliLivePacket.parse("not json", json)).isNull()
        assertThat(BilibiliLivePacket.decode(ByteArray(5))).isEmpty()
    }

    @Test
    fun `the auth body names the room, token and protocol version`() {
        val body = BilibiliLivePacket.authBody(roomId = 21452505, token = "tok", buvid = "b3")

        assertThat(body).contains("\"roomid\":21452505")
        assertThat(body).contains("\"key\":\"tok\"")
        assertThat(body).contains("\"protover\":${BilibiliLivePacket.PROTOCOL_VERSION}")
        assertThat(body).contains("\"buvid\":\"b3\"")
        assertThat(BilibiliLivePacket.authBody(1, "tok", null)).doesNotContain("buvid")
    }
}
