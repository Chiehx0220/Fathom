/*
 * The live chat wire format, as PipePipeExtractor (GPL-3.0) reads it in BilibiliWebSocketClient.java and
 * BilibiliLive*InfoItemExtractor.java. Copyright the PipePipeExtractor / NewPipeExtractor contributors.
 */
package io.github.aedev.flow.bilibili

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.util.zip.InflaterInputStream

/**
 * Chat packet: 16-byte header (length, header length, version, operation, sequence) + body.
 * Batches arrive as one zlib packet containing packets.
 */
internal object BilibiliLivePacket {
    const val OP_HEARTBEAT = 2
    const val OP_MESSAGE = 5
    const val OP_AUTH = 7

    private const val HEADER_SIZE = 16
    private const val VERSION_JSON = 1
    private const val VERSION_ZLIB = 2

    /** Auth packet version; brotli (3) unsupported (no decoder). */
    const val PROTOCOL_VERSION = VERSION_ZLIB

    private const val PROFILE_INDEX = 15
    private const val WHITE = 0xFFFFFF
    private const val RGB_MASK = 0xFFFFFF
    private const val OPAQUE = 0xFF000000.toInt()

    class Packet(
        val op: Int,
        val body: ByteArray,
    )

    fun encode(
        op: Int,
        body: String = "",
    ): ByteArray {
        val bytes = body.toByteArray(Charsets.UTF_8)
        return ByteBuffer
            .allocate(HEADER_SIZE + bytes.size)
            .putInt(HEADER_SIZE + bytes.size)
            .putShort(HEADER_SIZE.toShort())
            .putShort(VERSION_JSON.toShort())
            .putInt(op)
            .putInt(1)
            .put(bytes)
            .array()
    }

    fun authBody(
        roomId: Long,
        token: String,
        buvid: String?,
    ): String {
        val buvidField = if (buvid.isNullOrEmpty()) "" else ",\"buvid\":\"$buvid\""
        return "{\"uid\":0,\"roomid\":$roomId,\"protover\":$PROTOCOL_VERSION,\"platform\":\"web\"," +
            "\"clientver\":\"1.4.0\",\"type\":2,\"key\":\"$token\"$buvidField}"
    }

    /** All packets in [frame], incl. those inside a compressed packet. */
    fun decode(frame: ByteArray): List<Packet> {
        val packets = ArrayList<Packet>()
        val buffer = ByteBuffer.wrap(frame)
        var offset = 0
        while (offset + HEADER_SIZE <= frame.size) {
            val length = buffer.getInt(offset)
            val headerLength = buffer.getShort(offset + 4).toInt()
            val version = buffer.getShort(offset + 6).toInt()
            val op = buffer.getInt(offset + 8)
            if (headerLength < HEADER_SIZE || length < headerLength || offset + length > frame.size) break
            val body = frame.copyOfRange(offset + headerLength, offset + length)
            if (version == VERSION_ZLIB) {
                runCatching { InflaterInputStream(ByteArrayInputStream(body)).use { it.readBytes() } }
                    .onSuccess { packets += decode(it) }
            } else {
                packets += Packet(op, body)
            }
            offset += length
        }
        return packets
    }

    /** Events in [frame]; other packets (auth reply, popularity) yield none. */
    fun messages(
        frame: ByteArray,
        json: Json,
    ): List<BilibiliLiveMessage> =
        decode(frame)
            .filter { it.op == OP_MESSAGE }
            .mapNotNull { parse(it.body.toString(Charsets.UTF_8), json) }

    fun parse(
        body: String,
        json: Json,
    ): BilibiliLiveMessage? =
        runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            val cmd = root["cmd"]?.jsonPrimitive?.contentOrNull.orEmpty()
            when {
                cmd.startsWith("DANMU_MSG") -> chat(root["info"]?.jsonArray)
                cmd.startsWith("SUPER_CHAT_MESSAGE") && !cmd.endsWith("DELETE") -> superChat(root["data"]?.jsonObject)
                else -> null
            }
        }.getOrNull()

    /** `info[0]`: style (mode 1, colour 3, profile 15); `info[1]`: text; `info[2]`: sender (name 1). */
    private fun chat(info: JsonArray?): BilibiliLiveMessage.Chat? {
        val style = info.at(0) as? JsonArray ?: return null
        val text = info.at(1).string?.takeIf { it.isNotEmpty() } ?: return null
        val position =
            when ((style.at(1) as? JsonPrimitive)?.intOrNull) {
                4 -> BilibiliDanmakuPosition.BOTTOM
                5 -> BilibiliDanmakuPosition.TOP
                else -> BilibiliDanmakuPosition.SCROLL
            }
        val rgb = (style.at(3) as? JsonPrimitive)?.intOrNull ?: WHITE
        return BilibiliLiveMessage.Chat(
            text = text,
            argbColor = OPAQUE or (rgb and RGB_MASK),
            position = position,
            author =
                info
                    .at(2)
                    .at(1)
                    .string
                    .orEmpty(),
            avatarUrl =
                style
                    .at(PROFILE_INDEX)
                    .key("user")
                    .key("base")
                    .key("face")
                    .string
                    ?.takeIf { it.isNotBlank() },
        )
    }

    private fun superChat(data: JsonObject?): BilibiliLiveMessage.SuperChat? {
        val message = data.key("message").string?.takeIf { it.isNotEmpty() } ?: return null
        val body = data.argb("background_bottom_color") ?: (OPAQUE or WHITE)
        val sender = data.key("user_info")
        return BilibiliLiveMessage.SuperChat(
            message = message,
            priceYuan = (data.key("price") as? JsonPrimitive)?.intOrNull ?: 0,
            argbColor = body,
            headerArgbColor = data.argb("background_color_start") ?: body,
            author = sender.key("uname").string.orEmpty(),
            avatarUrl = sender.key("face").string?.takeIf { it.isNotBlank() },
        )
    }

    private fun JsonElement?.at(index: Int): JsonElement? = (this as? JsonArray)?.getOrNull(index)

    private fun JsonElement?.key(name: String): JsonElement? = (this as? JsonObject)?.get(name)

    private val JsonElement?.string: String? get() = (this as? JsonPrimitive)?.contentOrNull

    /** `#RRGGBB` field as opaque ARGB. */
    private fun JsonObject?.argb(name: String): Int? =
        key(name)
            .string
            ?.removePrefix("#")
            ?.toIntOrNull(16)
            ?.let { OPAQUE or (it and RGB_MASK) }
}
