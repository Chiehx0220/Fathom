package io.github.aedev.flow.innertube.pages

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shapes taken from a signed-in YouTube.com home response; every string in them is invented. */
class WebHomePageTest {
    private fun lockup(
        id: String,
        channel: String,
        browseId: String?,
        rows: String,
        badge: String,
        contentType: String = "LOCKUP_CONTENT_TYPE_VIDEO",
    ) = """
        {"richItemRenderer":{"content":{"lockupViewModel":{
          "contentId":"$id","contentType":"$contentType",
          "contentImage":{"thumbnailViewModel":{
            "image":{"sources":[{"url":"https://i.ytimg.com/vi/$id/small.jpg","width":168,"height":94},
                                {"url":"https://i.ytimg.com/vi/$id/big.jpg","width":720,"height":404}]},
            "overlays":[{"thumbnailBottomOverlayViewModel":{"badges":[{"thumbnailBadgeViewModel":{"text":"$badge"}}]}},
                        {"thumbnailHoverOverlayToggleActionsViewModel":{"buttons":[]}}]}},
          "metadata":{"lockupMetadataViewModel":{
            "title":{"content":"Title $id"},
            "image":{"decoratedAvatarViewModel":{
              "avatar":{"avatarViewModel":{"image":{"sources":[{"url":"//yt3.ggpht.com/$id=s68"}]}}},
              "rendererContext":{"commandContext":{"onTap":{"innertubeCommand":{"browseEndpoint":{"browseId":${browseId?.let { "\"$it\"" } ?: "null"}}}}}}}},
            "metadata":{"contentMetadataViewModel":{"metadataRows":[
              {"metadataParts":[{"text":{"content":"$channel"}}]},
              {"metadataParts":$rows}]}}}}}}}}
    """.trimIndent()

    private fun home(vararg items: String) =
        Json
            .parseToJsonElement(
                """{"contents":{"twoColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"richGridRenderer":{"contents":[${items.joinToString(",")}]}}}}]}}}""",
            ).jsonObject

    private val views = """[{"text":{"content":"12K views"}},{"text":{"content":"3 weeks ago"}}]"""

    @Test
    fun readsAVideoLockup() {
        val page = parseWebHomePage(home(lockup("vid1", "Some Channel", "UC123", views, "12:34")), "en", now = 1_000_000_000_000)
        val video = page.videos.single()

        assertEquals("vid1", video.id)
        assertEquals("Title vid1", video.title)
        assertEquals("Some Channel", video.channelName)
        assertEquals("UC123", video.channelId)
        assertEquals("https://i.ytimg.com/vi/vid1/big.jpg", video.thumbnailUrl)
        assertEquals(12 * 60 + 34, video.duration)
        assertEquals(12_000L, video.viewCount)
        assertEquals("3 weeks ago", video.uploadDate)
        assertEquals("https://yt3.ggpht.com/vid1=s68", video.channelThumbnailUrl)
        assertFalse(video.isLive)
    }

    @Test
    fun anHourLongDurationHasThreeParts() {
        val page = parseWebHomePage(home(lockup("vid1", "C", "UC1", views, "1:02:03")), "en")

        assertEquals(3600 + 2 * 60 + 3, page.videos.single().duration)
    }

    @Test
    fun aLiveStreamHasNoDurationAndNamesViewers() {
        val live = """[{"text":{"content":"1.2K watching"}}]"""
        val video = parseWebHomePage(home(lockup("live1", "C", "UC1", live, "LIVE")), "en").videos.single()

        assertTrue(video.isLive)
        assertEquals(0, video.duration)
    }

    @Test
    fun aMissingChannelIdStaysBlankRatherThanDroppingTheVideo() {
        val video = parseWebHomePage(home(lockup("vid1", "C", null, views, "1:00")), "en").videos.single()

        assertEquals("", video.channelId)
    }

    @Test
    fun skipsEverythingThatIsNotAVideo() {
        val page =
            parseWebHomePage(
                home(
                    lockup("playlist1", "C", "UC1", views, "5", contentType = "LOCKUP_CONTENT_TYPE_PLAYLIST"),
                    """{"richItemRenderer":{"content":{"adSlotRenderer":{}}}}""",
                    """{"richSectionRenderer":{"content":{"richShelfRenderer":{}}}}""",
                    lockup("vid1", "C", "UC1", views, "1:00"),
                ),
                "en",
            )

        assertEquals(listOf("vid1"), page.videos.map { it.id })
    }

    @Test
    fun readsTheContinuationAndDropsDuplicates() {
        val page =
            parseWebHomePage(
                home(
                    lockup("vid1", "C", "UC1", views, "1:00"),
                    lockup("vid1", "C", "UC1", views, "1:00"),
                    """{"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"NEXT"}}}}""",
                ),
                "en",
            )

        assertEquals(1, page.videos.size)
        assertEquals("NEXT", page.continuation)
    }

    @Test
    fun readsALaterPageFromTheContinuationAction() {
        val body =
            """{"onResponseReceivedActions":[{"appendContinuationItemsAction":{"continuationItems":[
                ${lockup("vid2", "C", "UC1", views, "2:00")},
                {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"MORE"}}}}]}}]}"""
        val page = parseWebHomePage(Json.parseToJsonElement(body).jsonObject, "en")

        assertEquals(listOf("vid2"), page.videos.map { it.id })
        assertEquals("MORE", page.continuation)
    }

    @Test
    fun anEmptyOrUnrecognisedResponseGivesAnEmptyPage() {
        val page = parseWebHomePage(Json.parseToJsonElement("""{"contents":{}}""").jsonObject, "en")

        assertTrue(page.videos.isEmpty())
        assertNull(page.continuation)
    }
}
