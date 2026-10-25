package io.github.aedev.flow.bilibili

import io.github.aedev.flow.bilibili.fake.verifyGolden
import io.github.aedev.flow.localserver.LocalServerBilibili
import io.github.aedev.flow.localserver.channelUrlToId
import io.github.aedev.flow.localserver.videoIdToUrl
import org.junit.Test

/** Id/service/url helpers, one row per input. */
class BilibiliIdTableGoldenTest {
    private val videoIds =
        listOf(
            "BV1GJ411x7h7",
            "BV1GJ411x7h7?p=1",
            "BV1GJ411x7h7?p=3",
            "BV1GJ411x7h7?p=0",
            "BV1GJ411x7h7?p=x",
            "BV1GJ411x7h7?p=2&spm=1",
            "BV1GJ411x7h",
            "BV1GJ411x7h77",
            "bv1GJ411x7h7",
            "live:545068",
            "live:",
            "live:abc",
            "live:545068?p=2",
            "dQw4w9WgXcQ",
            "-_aBcDeFgHi",
            "",
            "https://www.bilibili.com/video/BV1GJ411x7h7/?p=2&spm_id_from=333",
        )

    private val urls =
        listOf(
            "https://www.bilibili.com/video/BV1GJ411x7h7",
            "https://www.bilibili.com/video/BV1GJ411x7h7/?p=2&spm_id_from=333.1007",
            "https://m.bilibili.com/video/BV1GJ411x7h7?p=0",
            "https://live.bilibili.com/545068",
            "https://live.bilibili.com/blanc/545068?x=1",
            "https://live.bilibili.com/",
            "https://space.bilibili.com/546195",
            "https://space.bilibili.com/546195/video",
            "https://www.bilibili.com/space/546195",
            "https://b23.tv/abc",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com/channel/UC1234",
            "https://evilbilibili.com/video/BV1GJ411x7h7",
            "546195",
            " 546195 ",
            "UC1234",
            "",
        )

    private val channelIds = listOf("546195", "0", "", "12a", "UC1234", "@name", " 546195 ")

    private val playlistIds =
        listOf(
            BilibiliRef.Series(BilibiliPlaylistKind.SERIES, 546195, 81, "My series: 日本語 & more").id,
            BilibiliRef.Series(BilibiliPlaylistKind.SEASON, 546195, 71, "").id,
            "bilibili:series:1:2:name",
            "bilibili:SEASON:1:2:a%3Ab",
            "bilibili:other:1:2:n",
            "bilibili:series:x:2:n",
            "bilibili:series:1:2",
            "PLabc",
        )

    // pre-BilibiliRef link target form
    private fun legacyTarget(ref: BilibiliRef) =
        when (ref) {
            is BilibiliRef.Uploader -> "Uploader(mid=${ref.mid})"
            else -> "Video(videoId=${ref.id})"
        }

    @Test
    fun videoIdRows() {
        val rows =
            videoIds.joinToString("\n") { id ->
                listOf(
                    "id=$id",
                    "isBilibili=${BilibiliRef.isVideoId(id)}",
                    "isLive=${BilibiliRef.Live.ofId(id) != null}",
                    "roomId=${BilibiliRef.Live.ofId(id)?.roomId}",
                    "parse=${BilibiliRef.Video.parse(id).let { it.bvid to it.part }}",
                    "serviceOf=${serviceIdOfVideo(id)}",
                    "serviceOfSaved3=${serviceIdOfVideo(id, 3)}",
                    "toUrl=${LocalServerBilibili.videoUrl(id)}",
                    "idToUrl=${videoIdToUrl(id)}",
                ).joinToString(" | ")
            }
        verifyGolden("ids-video", rows)
    }

    @Test
    fun urlRows() {
        val rows =
            urls.joinToString("\n") { url ->
                listOf(
                    "url=$url",
                    "isBilibili=${BilibiliRef.isBilibiliUrl(url)}",
                    "video=${BilibiliRef.Video.ofUrl(url)?.id}",
                    "live=${BilibiliRef.Live.ofUrl(url)?.id}",
                    "mid=${BilibiliRef.Uploader.ofLink(url)?.mid}",
                    "channelUrlToId=${channelUrlToId(url)}",
                    "shortLink=${BilibiliDeepLink.isShortLink(url)}",
                    "deepLink=${BilibiliDeepLink.parse(url)?.let(::legacyTarget)}",
                ).joinToString(" | ")
            }
        verifyGolden("ids-url", rows)
    }

    @Test
    fun channelIdRows() {
        val rows =
            channelIds.joinToString("\n") { id ->
                "id=$id | isMid=${BilibiliRef.Uploader.ofId(
                    id,
                ) != null} | serviceOf=${serviceIdOfChannel(id)} | serviceOfSaved3=${serviceIdOfChannel(id, 3)}"
            }
        verifyGolden("ids-channel", rows)
    }

    @Test
    fun playlistIdRows() {
        val rows =
            playlistIds.joinToString(
                "\n",
            ) { id ->
                "id=$id | parse=${BilibiliRef.Series.ofId(
                    id,
                )?.let { "Parsed(kind=${it.kind}, mid=${it.mid}, id=${it.seriesId}, name=${it.name})" }}"
            }
        verifyGolden("ids-playlist", rows)
    }
}
