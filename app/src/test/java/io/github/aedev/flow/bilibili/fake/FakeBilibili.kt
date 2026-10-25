package io.github.aedev.flow.bilibili.fake

import io.github.aedev.flow.bilibili.BilibiliApi
import io.github.aedev.flow.bilibili.BilibiliSession
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList

/** Offline Bilibili: path-keyed fixtures (`resources/bilibili`), request log. */
internal class FakeBilibili : Interceptor {
    /** host+path → fixture name or inline body (`=` prefix). */
    private val routes = LinkedHashMap<String, String>()
    private val queue = HashMap<String, ArrayDeque<String>>()
    val requests = CopyOnWriteArrayList<String>()
    private val log = CopyOnWriteArrayList<String>()

    init {
        routes[SPI] = "=" + """{"code":0,"data":{"b_3":"B3","b_4":"B4"}}"""
        routes[NAV] =
            "=" +
            """{"data":{"wbi_img":{"img_url":"https://i0.hdslb.com/bfs/wbi/7cd084941338484aae1ad9425b84077c.png","sub_url":"https://i0.hdslb.com/bfs/wbi/4932caff0ff746eab6f01bf08b70ac45.png"}}}"""
    }

    /** Route [path] (host+path) to fixture [name]. */
    fun serve(
        path: String,
        name: String,
    ) = apply { routes[path] = name }

    /** One-shot answers for [path]; then the route. */
    fun serveOnce(
        path: String,
        vararg names: String,
    ) = apply { queue.getOrPut(path) { ArrayDeque() }.addAll(names) }

    fun session(): BilibiliSession = BilibiliSession(OkHttpClient.Builder().addInterceptor(this).build())

    fun api(loggedInCookie: String? = null) = BilibiliApi(session(), { loggedInCookie })

    fun count(path: String) = requests.count { it.startsWith(path) }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val key = request.url.host + request.url.encodedPath
        requests += key + (request.url.encodedQuery?.let { "?$it" } ?: "")
        log += logLine(request)
        val name =
            queue[key]?.removeFirstOrNull()
                ?: if (key == TICKET) ticketBody else routes[key] ?: error("Unrouted request: $key")
        val body = if (name.startsWith("=")) name.drop(1) else fixture(name)
        val type = if (name.endsWith(".html")) "text/html" else "application/json"
        return Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(if (name.endsWith(".html")) 412 else 200)
            .message("fake")
            .header("date", DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()))
            .body(body.toResponseBody(type.toMediaType()))
            .build()
    }

    /** Request shapes; params sorted, volatile values masked. */
    fun requestLog(): String = log.joinToString("\n", postfix = "\n")

    private fun logLine(request: okhttp3.Request): String {
        val url = request.url
        val params =
            url.queryParameterNames.sorted().joinToString("&") { name ->
                val value = if (name in VOLATILE) "*" else url.queryParameter(name)
                "$name=$value"
            }
        val cookie =
            when (val raw = request.header("Cookie")) {
                null -> "none"
                "" -> "empty"
                else -> if ("buvid3=" in raw) "anon" else raw
            }
        return "${url.host}${url.encodedPath}" + (if (params.isNotEmpty()) "?$params" else "") + "  cookie=$cookie"
    }

    private val ticketBody: String
        get() = "=" + """{"code":0,"data":{"ticket":"T","created_at":${System.currentTimeMillis() / 1000},"ttl":259200}}"""

    private fun fixture(name: String): String =
        javaClass.classLoader!!
            .getResourceAsStream("bilibili/$name")!!
            .bufferedReader()
            .readText()

    companion object {
        private val VOLATILE =
            setOf("w_rid", "wts", "dm_img_list", "dm_img_str", "dm_cover_img_str", "dm_img_inter", "ts", "sign", "hexsign", "context[ts]")
        private const val SPI = "api.bilibili.com/x/frontend/finger/spi"
        private const val NAV = "api.bilibili.com/x/web-interface/nav"
        private const val TICKET = "api.bilibili.com/bapis/bilibili.api.ticket.v1.Ticket/GenWebTicket"

        const val VIEW = "api.bilibili.com/x/web-interface/wbi/view"
        const val PLAYURL = "api.bilibili.com/x/player/wbi/playurl"
        const val PLAYER_V2 = "api.bilibili.com/x/player/wbi/v2"
        const val DANMAKU = "api.bilibili.com/x/v1/dm/list.so"
        const val RELATED = "api.bilibili.com/x/web-interface/archive/related"
        const val POPULAR = "api.bilibili.com/x/web-interface/popular"
        const val SEARCH = "api.bilibili.com/x/web-interface/search/type"
        const val CARD = "api.bilibili.com/x/web-interface/card"
        const val VIDEOS_WEB = "api.bilibili.com/x/space/wbi/arc/search"
        const val VIDEOS_SEARCH = "api.bilibili.com/x/series/recArchivesByKeywords"
        const val VIDEOS_APP = "app.bilibili.com/x/v2/space/archive/cursor"
        const val SEASONS_SERIES = "api.bilibili.com/x/polymer/web-space/seasons_series_list"
        const val SEASON_ARCHIVES = "api.bilibili.com/x/polymer/web-space/seasons_archives_list"
        const val SERIES_ARCHIVES = "api.bilibili.com/x/series/archives"
        const val COMMENTS = "api.bilibili.com/x/v2/reply/wbi/main"
        const val REPLIES = "api.bilibili.com/x/v2/reply/reply"
        const val LIVE_BASE = "api.live.bilibili.com/xlive/web-room/v1/index/getRoomBaseInfo"
        const val LIVE_PLAY = "api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo"
        const val LIVE_RECOMMENDED = "api.live.bilibili.com/room/v1/room/get_user_recommend"
        const val LIVE_STATUS = "api.live.bilibili.com/room/v1/Room/get_status_info_by_uids"
        const val DANMU_INFO = "api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo"
    }
}
