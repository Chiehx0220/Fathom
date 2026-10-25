package io.github.aedev.flow.localserver

import android.app.Application
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.stream.StreamType

/** Network-backed route JSON from fixed extractor results. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class)
class RendererBaselineTest {
    @Test
    fun `search and listing results`() {
        val items =
            listOf(
                stream("aaaaaaaaaaa"),
                stream("bbbbbbbbbbb", StreamType.LIVE_STREAM, 0),
                stream("ccccccccccc", duration = 45),
                channel(),
                playlist(),
            )
        val text =
            buildString {
                append("search\n").append(canonicalJson(ApiJson.encodeToString(items.toSearchResult(Page("https://next/page", "id1")))))
                append("\n\nlast page\n").append(canonicalJson(ApiJson.encodeToString(items.take(1).toSearchResult(null))))
                append("\n\nmixed list\n").append(canonicalJson(ApiJson.encodeToString(items.toCards())))
                append("\n\nerror\n").append(canonicalJson(failureBody(json = true, 400, "boom").first))
                append("\n\nerror without message\n").append(canonicalJson(failureBody(json = true, 400, null).first))
                append("\n")
            }
        verifyBaseline("renderer-listings", text)
    }

    @Test
    fun `channel header and comments`() {
        val header = ChannelHeader("Channel", "https://www.youtube.com/channel/UCchan", "https://yt3.example/chan.jpg", null, 4200, "About")
        val noAvatar = ChannelHeader("Bare", "https://www.youtube.com/channel/UCbare", null, null, -5, "")
        val text =
            buildString {
                append("channel\n").append(canonicalJson(ApiJson.encodeToString(header.toDto(true))))
                append("\n\nchannel without avatar\n").append(canonicalJson(ApiJson.encodeToString(noAvatar.toDto(false))))
                append("\n\ncomments\n")
                append(canonicalJson(ApiJson.encodeToString(listOf(comment("c1", false), comment("c2", true)).map { it.toDto() })))
                append("\n")
            }
        verifyBaseline("renderer-channel-comments", text)
    }

    @Test
    fun `video detail`() {
        verifyBaseline("renderer-video-detail", canonicalJson(ApiJson.encodeToString(zooInfo().toDetail(0))) + "\n")
    }
}
