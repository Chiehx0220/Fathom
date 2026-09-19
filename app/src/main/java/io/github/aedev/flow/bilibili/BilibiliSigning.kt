/*
 * Ported from PipePipeExtractor (GPL-3.0), commit aef9726d5b1172213066f60bc338eb4278651d61:
 * extractor/src/main/java/org/schabi/newpipe/extractor/services/bilibili/utils.java
 * Copyright the PipePipeExtractor / NewPipeExtractor contributors.
 */
package io.github.aedev.flow.bilibili

import java.math.BigInteger
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.TreeMap
import kotlin.random.Random

/**
 * Everything that is pure computation in Bilibili's request validation: the av/bv id codec, the WBI
 * signature, and the fake "danmaku image" telemetry (dm_img_*) the web player attaches. Nothing
 * here touches the network, so it is all unit-testable; the WBI mixin key itself is fetched by
 * [BilibiliSession].
 */
object BilibiliSigning {
    // region av <-> bv
    private val XOR_CODE = BigInteger("23442827791579")
    private val MASK_CODE = BigInteger("2251799813685247")
    private val MAX_AID = BigInteger.ONE.shiftLeft(51)
    private val BASE = BigInteger("58")
    private const val TABLE = "FcwAPNKTMug3GV5Lj7EJnHpWsx4tb8haYeviqBz6rkCy12mUSDQX9RdoZf"

    fun av2bv(aid: Long): String {
        val bytes = charArrayOf('B', 'V', '1', '0', '0', '0', '0', '0', '0', '0', '0', '0')
        var bvIndex = bytes.size - 1
        var tmp = MAX_AID.or(BigInteger.valueOf(aid)).xor(XOR_CODE)
        while (tmp > BigInteger.ZERO) {
            bytes[bvIndex] = TABLE[tmp.mod(BASE).toInt()]
            tmp = tmp.divide(BASE)
            bvIndex -= 1
        }
        bytes[3] = bytes[9].also { bytes[9] = bytes[3] }
        bytes[4] = bytes[7].also { bytes[7] = bytes[4] }
        return String(bytes)
    }

    fun bv2av(bvid: String): Long {
        val chars = bvid.toCharArray()
        chars[3] = chars[9].also { chars[9] = chars[3] }
        chars[4] = chars[7].also { chars[7] = chars[4] }
        var tmp = BigInteger.ZERO
        for (c in String(chars, 3, chars.size - 3)) {
            tmp = tmp.multiply(BASE).add(BigInteger.valueOf(TABLE.indexOf(c).toLong()))
        }
        return tmp.and(MASK_CODE).xor(XOR_CODE).toLong()
    }
    // endregion

    // region WBI signature
    private val MIXIN_KEY_ORDER =
        intArrayOf(
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39,
            12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63,
            57, 62, 11, 36, 20, 34, 44, 52,
        )

    /** The 32-char mixin key, shuffled out of the two file-name stems in the nav API's wbi_img. */
    fun mixinKey(imgValue: String, subValue: String): String {
        val raw = imgValue + subValue
        return MIXIN_KEY_ORDER.map { raw[it] }.joinToString("").substring(0, 32)
    }

    fun percentSpaceEncode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    /**
     * Adds w_rid and wts to [params] and returns the query string, in the shape PipePipe sends it:
     * the hash is over the percent-encoded, key-sorted query, but the query that goes on the wire is
     * the plain "key=value" join of the insertion-ordered params - the HTTP layer does the escaping.
     */
    fun signWbi(
        params: LinkedHashMap<String, String>,
        mixinKey: String,
        wtsSeconds: Long,
    ): String {
        val sorted = TreeMap(params)
        sorted["wts"] = wtsSeconds.toString()
        val toSign =
            sorted.entries.joinToString("&") { percentSpaceEncode(it.key) + "=" + percentSpaceEncode(it.value) }
        val digest = MessageDigest.getInstance("MD5").digest((toSign + mixinKey).toByteArray(Charsets.UTF_8))
        params["w_rid"] = digest.toHex()
        params["wts"] = wtsSeconds.toString()
        return params.entries.joinToString("&") { it.key + "=" + it.value }
    }
    // endregion

    // region App signature
    private const val APP_KEY = "1d8b6e7d45233436"
    private const val APP_SEC = "560c52ccd288fed045859ed18bffd973"

    /** Adds appkey and sign to [params] and returns the query string, as PipePipe's encAppSign does. */
    fun signApp(params: LinkedHashMap<String, String>): String {
        params["appkey"] = APP_KEY
        val toSign = TreeMap(params).entries.joinToString("&") { URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8") }
        params["sign"] = MessageDigest.getInstance("MD5").digest((toSign + APP_SEC).toByteArray(Charsets.UTF_8)).toHex()
        return params.entries.joinToString("&") { it.key + "=" + it.value }
    }
    // endregion

    // region dm_img telemetry
    private fun wh(width: Int, height: Int, random: Random): IntArray {
        val rnd = random.nextInt(114)
        return intArrayOf(2 * width + 2 * height + 3 * rnd, 4 * width - height + rnd, rnd)
    }

    private fun of(scrollTop: Int, scrollLeft: Int, random: Random): IntArray {
        val rnd = random.nextInt(514)
        return intArrayOf(3 * scrollTop + 2 * scrollLeft + rnd, 4 * scrollTop - 4 * scrollLeft + 2 * rnd, rnd)
    }

    fun dmImgParams(
        device: DeviceForger.Device = DeviceForger.requireRandomDevice(),
        random: Random = Random.Default,
    ): LinkedHashMap<String, String> {
        val params = LinkedHashMap<String, String>()
        params["dm_img_list"] = "[]"
        params["dm_img_str"] = device.webGlVersionBase64
        params["dm_cover_img_str"] = device.webGlRendererInfoBase64
        val wh = wh(device.innerWidth, device.innerHeight, random)
        val of = of(0, 0, random)
        params["dm_img_inter"] = "{\"ds\":[],\"wh\":[${wh.joinToString(",")}],\"of\":[${of.joinToString(",")}]}"
        return params
    }
    // endregion

    internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
