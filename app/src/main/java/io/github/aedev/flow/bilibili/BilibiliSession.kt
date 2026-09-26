/*
 * Ported from PipePipeExtractor (GPL-3.0), commit aef9726d5b1172213066f60bc338eb4278651d61:
 * extractor/src/main/java/org/schabi/newpipe/extractor/services/bilibili/BilibiliService.java
 * (getDefaultCookies, getBiliTicket, getFpUuid, getUserAgentHeaders, getHeaders)
 * and utils.java (encWbi key fetch).
 * Copyright the PipePipeExtractor / NewPipeExtractor contributors.
 */
package io.github.aedev.flow.bilibili

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * What makes a request look like a real logged-out browser to Bilibili: forged device headers, the
 * anonymous cookie set (buvid3/4, b_nut, b_lsid, _uuid, bili_ticket, buvid_fp) and the daily WBI
 * mixin key. One instance per app; the cookie set and the key are cached and refreshed on expiry.
 *
 * When Bilibili changes what its risk control checks, this is the file that changes - and the file
 * to diff against PipePipeExtractor's BilibiliService/utils.
 */
class BilibiliSession(
    private val http: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val cookieLock = Mutex()
    private var cookies: LinkedHashMap<String, String>? = null

    private val keyLock = Mutex()
    private var mixinKey: String? = null
    private var mixinKeyDate: LocalDate? = null

    /** UA + Referer + Accept-Language, no cookies. [originalUrl]'s host picks the Referer. */
    fun userAgentHeaders(originalUrl: String?): LinkedHashMap<String, String> {
        val headers = LinkedHashMap<String, String>()
        headers["User-Agent"] = DeviceForger.requireRandomDevice().userAgent
        if (originalUrl != null) {
            var referer = "https://" + URI(originalUrl).host + "/"
            if (referer !in KNOWN_REFERERS) referer = WWW_REFERER
            headers["Referer"] = referer
        }
        headers["Accept-Language"] = "zh-CN,zh;q=0.9"
        return headers
    }

    /** [userAgentHeaders] plus the anonymous default cookie set. */
    suspend fun headers(originalUrl: String?): LinkedHashMap<String, String> {
        val headers = userAgentHeaders(originalUrl)
        headers["Cookie"] = cookieHeader(defaultCookies())
        return headers
    }

    suspend fun defaultCookies(): LinkedHashMap<String, String> =
        cookieLock.withLock {
            val current = cookies
            val now = Instant.now().epochSecond
            if (current != null && (current["bili_ticket_expires"]?.toLongOrNull() ?: 0L) > now) {
                return@withLock current
            }
            fetchDefaultCookies().also { cookies = it }
        }

    /** Forces the next call to fetch a fresh cookie set and a fresh device, e.g. after a risk-control block. */
    suspend fun reset() {
        cookieLock.withLock { cookies = null }
        keyLock.withLock {
            mixinKey = null
            mixinKeyDate = null
        }
        DeviceForger.regenerateRandomDevice()
    }

    /** Signs [params] for a WBI endpoint and returns the full URL. */
    suspend fun signedUrl(
        baseUrl: String,
        params: LinkedHashMap<String, String>,
    ): String {
        val key = wbiMixinKey()
        val query = BilibiliSigning.signWbi(params, key, Math.round(System.currentTimeMillis() / 1000f).toLong())
        return "$baseUrl?$query"
    }

    private suspend fun wbiMixinKey(): String =
        keyLock.withLock {
            val today = LocalDate.now(ZoneId.of("Asia/Shanghai"))
            val cached = mixinKey
            val cachedDate = mixinKeyDate
            if (cached != null && cachedDate != null && !cachedDate.isBefore(today)) return@withLock cached

            val body = get(WBI_IMG_URL, headers(WWW_REFERER))
            val imgUrl = IMG_URL_REGEX.find(body)?.groupValues?.get(1) ?: throw IOException("wbi_img.img_url missing")
            val subUrl = SUB_URL_REGEX.find(body)?.groupValues?.get(1) ?: throw IOException("wbi_img.sub_url missing")
            val key = BilibiliSigning.mixinKey(stem(imgUrl), stem(subUrl))
            mixinKey = key
            mixinKeyDate = today
            key
        }

    private fun stem(url: String): String = url.substringAfterLast('/').substringBefore('.')

    private suspend fun fetchDefaultCookies(): LinkedHashMap<String, String> {
        val spi =
            execute(
                Request
                    .Builder()
                    .url(FETCH_COOKIE_URL)
                    .apply {
                        userAgentHeaders(WWW_REFERER).forEach { (k, v) ->
                            header(k, v)
                        }
                    }.build(),
            )
        val data =
            json.parseToJsonElement(spi.body).jsonObject["data"]?.jsonObject
                ?: throw IOException("finger/spi returned no data")
        val fresh = LinkedHashMap<String, String>()
        fresh["buvid3"] = data["b_3"]?.jsonPrimitive?.contentOrNull ?: throw IOException("b_3 missing")
        fresh["b_nut"] =
            ZonedDateTime
                .parse(spi.date ?: throw IOException("date header missing"), DateTimeFormatter.RFC_1123_DATE_TIME)
                .toEpochSecond()
                .toString()
        val lsid = Random.Default.nextBytes(32).toHexUpper()
        fresh["b_lsid"] = "${lsid}_${java.lang.Long.toHexString(System.currentTimeMillis()).uppercase(Locale.ROOT)}"
        fresh["_uuid"] = fpUuid()
        fresh["buvid4"] = data["b_4"]?.jsonPrimitive?.contentOrNull ?: throw IOException("b_4 missing")

        val (ticket, expires) = biliTicket(csrf = "", cookies = fresh)
        fresh["bili_ticket"] = ticket
        fresh["bili_ticket_expires"] = expires.toString()
        fresh["buvid_fp"] = Random.Default.nextBytes(16).toHexLower()
        return fresh
    }

    /** See https://github.com/SocialSisterYi/bilibili-API-collect/blob/master/docs/misc/sign/bili_ticket.md */
    private suspend fun biliTicket(
        csrf: String,
        cookies: LinkedHashMap<String, String>,
    ): Pair<String, Long> {
        val ts = Instant.now().epochSecond
        val hexSign = hmacSha256("XgwSnGZ1p", "ts$ts")
        val url = "$FETCH_TICKET_URL?key_id=ec02&hexsign=$hexSign&context[ts]=$ts&csrf=$csrf"
        val builder = Request.Builder().url(url).post(ByteArray(0).toRequestBody())
        userAgentHeaders(WWW_REFERER).forEach { (k, v) -> builder.header(k, v) }
        builder.header("Cookie", cookieHeader(cookies))
        val data =
            json.parseToJsonElement(execute(builder.build()).body).jsonObject["data"]?.jsonObject
                ?: throw IOException("GenWebTicket returned no data")
        val ticket = data["ticket"]?.jsonPrimitive?.contentOrNull ?: throw IOException("ticket missing")
        val createdAt = data["created_at"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
        val ttl = data["ttl"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
        require(createdAt > 0) { "created_at: $createdAt" }
        require(ttl > 0) { "ttl: $ttl" }
        return ticket to (createdAt + ttl)
    }

    /** See https://github.com/SocialSisterYi/bilibili-API-collect/issues/933 */
    private fun fpUuid(): String {
        val digitMap = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D", "E", "F", "10")
        val t = System.currentTimeMillis() % 100_000
        val index = Random.Default.nextBytes(32)
        val hyphenIndices = setOf(9, 13, 17, 21)
        val result = StringBuilder(64)
        for (ii in index.indices) {
            if (ii in hyphenIndices) result.append('-')
            result.append(digitMap[index[ii].toInt() and 0x0f])
        }
        result.append(String.format(Locale.ROOT, "%05d", t))
        result.append("infoc")
        return result.toString()
    }

    private fun hmacSha256(
        key: String,
        message: String,
    ): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(message.toByteArray(Charsets.UTF_8)).toHexLower()
    }

    private fun cookieHeader(cookies: Map<String, String>): String = cookies.entries.joinToString("; ") { it.key + "=" + it.value }

    private class Result(
        val body: String,
        val date: String?,
    )

    private suspend fun execute(request: Request): Result =
        withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} for ${request.url.host}")
                Result(response.body?.string().orEmpty(), response.header("date"))
            }
        }

    internal suspend fun get(
        url: String,
        headers: Map<String, String>,
    ): String = execute(request(url, headers)).body

    /** For endpoints that answer with something other than text. */
    internal suspend fun getBytes(
        url: String,
        headers: Map<String, String>,
    ): ByteArray =
        withContext(Dispatchers.IO) {
            val request = request(url, headers)
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} for ${request.url.host}")
                response.body?.bytes() ?: ByteArray(0)
            }
        }

    /** Returns the body whatever the HTTP status: Bilibili's risk control answers with a 412 and a page. */
    internal suspend fun getLenient(
        url: String,
        headers: Map<String, String>,
    ): String =
        withContext(Dispatchers.IO) {
            http.newCall(request(url, headers)).execute().use { it.body?.string().orEmpty() }
        }

    private fun request(
        url: String,
        headers: Map<String, String>,
    ): Request {
        val builder = Request.Builder().url(url)
        headers.forEach { (k, v) -> builder.header(k, v) }
        return builder.build()
    }

    private fun ByteArray.toHexLower(): String = joinToString("") { "%02x".format(it) }

    private fun ByteArray.toHexUpper(): String = joinToString("") { "%02X".format(it) }

    companion object {
        const val WWW_REFERER = "https://www.bilibili.com/"
        const val SPACE_REFERER = "https://space.bilibili.com/"
        const val LIVE_REFERER = "https://live.bilibili.com/"
        private val KNOWN_REFERERS = setOf(WWW_REFERER, SPACE_REFERER, LIVE_REFERER)

        const val WBI_IMG_URL = "https://api.bilibili.com/x/web-interface/nav"
        const val FETCH_COOKIE_URL = "https://api.bilibili.com/x/frontend/finger/spi"
        const val FETCH_TICKET_URL = "https://api.bilibili.com/bapis/bilibili.api.ticket.v1.Ticket/GenWebTicket"

        private val IMG_URL_REGEX = Regex("\"img_url\":\"([^\"]*)\"")
        private val SUB_URL_REGEX = Regex("\"sub_url\":\"([^\"]*)\"")
    }
}
