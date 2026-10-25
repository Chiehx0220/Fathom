package io.github.aedev.flow.localserver

import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.stream.StreamInfo

/** Default test source; any call fails. */
internal object NoNetwork : ContentSource {
    private fun refuse(): Nothing = error("network source used")

    override fun home(
        serviceId: Int,
        nextPage: Page?,
    ) = refuse()

    override fun trending(serviceId: Int) = refuse()

    override fun search(
        serviceId: Int,
        query: String,
        nextPage: Page?,
    ) = refuse()

    override fun channel(
        serviceId: Int,
        channelUrl: String,
        tab: String,
        sort: String?,
        nextPage: Page?,
    ) = refuse()

    override fun playlist(
        serviceId: Int,
        playlistUrl: String,
        nextPage: Page?,
    ) = refuse()

    override fun comments(
        serviceId: Int,
        videoUrl: String,
        nextPage: Page?,
    ) = refuse()

    override fun streamInfo(
        serviceId: Int,
        mediaUrl: String,
    ) = refuse()

    override fun watchInfo(
        serviceId: Int,
        mediaUrl: String,
    ) = refuse()

    override fun danmaku(
        serviceId: Int,
        mediaUrl: String,
    ) = refuse()

    override fun sponsorSegments(videoId: String) = refuse()
}

/** Fixed results; [calls] shows what the routes asked for. */
internal class FakeSource : ContentSource {
    val calls = mutableListOf<String>()

    private val more = Page("more:2", "cursor")

    private fun items(): List<InfoItem> = listOf(stream("aaaaaaaaaaa"), channel(), playlist())

    override fun home(
        serviceId: Int,
        nextPage: Page?,
    ): ListPage {
        calls += "home service=$serviceId next=${nextPage?.url}"
        return ListPage(items(), more.takeIf { nextPage == null })
    }

    override fun trending(serviceId: Int): List<InfoItem> {
        calls += "trending service=$serviceId"
        return listOf(stream("ttttttttttt"), stream("uuuuuuuuuuu", duration = 60))
    }

    override fun search(
        serviceId: Int,
        query: String,
        nextPage: Page?,
    ): ListPage {
        calls += "search service=$serviceId q=$query next=${nextPage?.url}"
        return if (nextPage == null) ListPage(items(), more) else ListPage(listOf(stream("ddddddddddd")), null)
    }

    override fun channel(
        serviceId: Int,
        channelUrl: String,
        tab: String,
        sort: String?,
        nextPage: Page?,
    ): ChannelPage {
        calls += "channel service=$serviceId url=$channelUrl tab=$tab sort=$sort next=${nextPage?.url}"
        val header = ChannelHeader("Channel", channelUrl, "https://yt3.example/chan.jpg", null, 4200, "About")
        return ChannelPage(header, listOf(stream("ccccccccccc")), more.takeIf { nextPage == null })
    }

    override fun playlist(
        serviceId: Int,
        playlistUrl: String,
        nextPage: Page?,
    ): PlaylistPage {
        calls += "playlist service=$serviceId url=$playlistUrl next=${nextPage?.url}"
        return PlaylistPage(
            PlaylistHeader("Playlist", playlistUrl, "Uploader", 12),
            listOf(stream("ppppppppppp")),
            more.takeIf {
                nextPage ==
                    null
            },
        )
    }

    override fun comments(
        serviceId: Int,
        videoUrl: String,
        nextPage: Page?,
    ): CommentsResult {
        calls += "comments service=$serviceId url=$videoUrl next=${nextPage?.url}"
        if ("disabled" in videoUrl) return CommentsResult(emptyList(), null, disabled = true)
        return CommentsResult(listOf(comment("c1", false), comment("c2", true)), more.takeIf { nextPage == null })
    }

    override fun streamInfo(
        serviceId: Int,
        mediaUrl: String,
    ): StreamInfo {
        calls += "streamInfo service=$serviceId url=$mediaUrl"
        return zooInfo()
    }

    override fun watchInfo(
        serviceId: Int,
        mediaUrl: String,
    ): StreamInfo {
        calls += "watchInfo service=$serviceId url=$mediaUrl"
        return zooInfo()
    }

    override fun danmaku(
        serviceId: Int,
        mediaUrl: String,
    ): List<Bullet> {
        calls += "danmaku service=$serviceId url=$mediaUrl"
        return if (serviceId ==
            5
        ) {
            listOf(Bullet("hi", "#FFFFFF", "scroll", 2.5, 0.6f), Bullet("top", "#FF0000", "top", 3.0, 1.0f))
        } else {
            emptyList()
        }
    }

    override fun sponsorSegments(videoId: String): List<SponsorBlockClient.Segment> {
        calls += "sponsor video=$videoId"
        return listOf(
            SponsorBlockClient.Segment(0, 4_199, "intro"),
            SponsorBlockClient.Segment(10_000, 20_500, "interaction"),
            SponsorBlockClient.Segment(30_000, 31_000, "filler"),
            SponsorBlockClient.Segment(40_000, 50_000, "music_offtopic"),
        )
    }
}
