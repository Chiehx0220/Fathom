/*
 * The live chat wire format, as PipePipeExtractor (GPL-3.0) reads it in BilibiliWebSocketClient.java and
 * BilibiliLive*InfoItemExtractor.java. Copyright the PipePipeExtractor / NewPipeExtractor contributors.
 */
package io.github.aedev.flow.bilibili

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.util.zip.InflaterInputStream

/**
 * A chat packet is a 16-byte header (length, header length, version, operation, sequence) and a body.
 * The server batches messages into one zlib-compressed packet, which itself holds packets.
 */
internal object BilibiliLivePacket {
    const val OP_HEARTBEAT = 2
    const val OP_MESSAGE = 5
    const val OP_AUTH = 7

    private const val HEADER_SIZE = 16
    private const val VERSION_JSON = 1
    private const val VERSION_ZLIB = 2

    /** The version asked for in the auth packet; brotli (3) would need a decoder Flow does not carry. */
    const val PROTOCOL_VERSION = VERSION_ZLIB

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

    /** Every packet in [frame], the ones inside a compressed packet included. */
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

    /** The events in [frame]; other packets (auth reply, popularity) carry none. */
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

    /** info[0] holds the style (mode at 1, colour at 3), info[1] the text. */
    private fun chat(info: JsonArray?): BilibiliLiveMessage.Chat? {
        val style = info?.getOrNull(0)?.jsonArray ?: return null
        val text =
            info
                .getOrNull(1)
                ?.jsonPrimitive
                ?.contentOrNull
                ?.takeIf { it.isNotEmpty() } ?: return null
        val position =
            when (style.getOrNull(1)?.jsonPrimitive?.intOrNull) {
                4 -> BilibiliDanmakuPosition.BOTTOM
                5 -> BilibiliDanmakuPosition.TOP
                else -> BilibiliDanmakuPosition.SCROLL
            }
        val rgb = style.getOrNull(3)?.jsonPrimitive?.intOrNull ?: WHITE
        return BilibiliLiveMessage.Chat(text, OPAQUE or (rgb and RGB_MASK), position)
    }

    private fun superChat(data: JsonObject?): BilibiliLiveMessage.SuperChat? {
        val message =
            data
                ?.get("message")
                ?.jsonPrimitive
                ?.contentOrNull
                ?.takeIf { it.isNotEmpty() } ?: return null
        val price = data["price"]?.jsonPrimitive?.intOrNull ?: 0
        val rgb =
            data["background_bottom_color"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.removePrefix("#")
                ?.toIntOrNull(16)
                ?: WHITE
        return BilibiliLiveMessage.SuperChat("(¥$price) $message", price, OPAQUE or (rgb and RGB_MASK))
    }
}
