package io.github.aedev.flow.localserver

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.ktor.http.HttpMethod
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

private const val VIDEO = "https://www.youtube.com/watch?v=jNQXAC9IVRw"
private const val CHANNEL = "https://www.youtube.com/channel/UC4QobU6STFB0P71PMvOGN5A"
private const val BLOCKED = "https://www.youtube.com/channel/UCblocked0000000000000000"
private const val PLAYLIST = "https://www.youtube.com/playlist?list=PLbaseline"
private const val CLIP = "title=Me+at+the+zoo&uploader=jawed&thumbnail=http%3A%2F%2Ft%2Fz.jpg&uploaderUrl=$CHANNEL"

/** Per-route web-client JSON; UPDATE_BASELINES=1 rewrites. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class)
class RouteBaselineTest {
    @Test
    fun guards() =
        serverScenario {
            record("guards") {
                get("/nope")
                get("/vendor/none.bin")
                get("/api/v1/history", mapOf("Host" to "evil.example.com"))
                call(HttpMethod.Options, "/api/v1/history")
                call(HttpMethod.Options, "/stream", mapOf("Origin" to "http://other.example.com"))
                call(HttpMethod.Post, "/playlist_action?action=create&name=x", mapOf("Origin" to "http://evil.example.com"))
                get("/remote", mapOf("Origin" to "http://evil.example.com"))
            }
        }

    @Test
    fun `missing parameters`() =
        serverScenario {
            record("missing-parameters") {
                for (path in WITHOUT_PARAMETERS) get(path)
            }
        }

    @Test
    fun library() =
        serverScenario {
            record("library") {
                get("/history_action?action=clear")
                get("/subscribe?action=subscribe&id=$CHANNEL&name=jawed&avatar=http%3A%2F%2Ft%2Fa.jpg")
                get("/block_channel?action=block&id=$BLOCKED")
                get("/bookmark_playlist?action=bookmark&id=$PLAYLIST&name=Baseline+mix")
                get("/watch_later_action?action=add&url=$VIDEO&$CLIP")
                get("/rate_video?action=like&url=$VIDEO&$CLIP")
                get("/api/v1/state?id=$VIDEO&channel=$CHANNEL")
                get("/api/v1/state?id=$VIDEO&channel=$BLOCKED")
                get("/api/v1/library")
                get("/api/v1/liked")
                get("/rate_video?action=dislike&url=$VIDEO&$CLIP")
                get("/api/v1/state?id=$VIDEO")
                get("/rate_video?action=remove&url=$VIDEO")
                get("/api/v1/state?id=$VIDEO")
                get("/watch_later_action?action=remove&url=$VIDEO")
                get("/bookmark_playlist?action=unbookmark&id=$PLAYLIST")
                get("/block_channel?action=unblock&id=$BLOCKED")
                get("/subscribe?action=unsubscribe&id=$CHANNEL")
                get("/api/v1/library")
            }
        }

    @Test
    fun playlists() =
        serverScenario {
            record("playlists") {
                val created = JSONObject(get("/playlist_action?action=create&name=Study&url=$VIDEO&$CLIP")).getString("playlist")
                mask(created, "<playlist>")
                get("/api/v1/playlists?id=$VIDEO")
                get("/api/v1/mylist?id=$created")
                get("/playlist_action?action=rename&playlist=$created&name=Study+2")
                get("/playlist_action?action=remove&playlist=$created&url=$VIDEO")
                get("/api/v1/mylist?id=$created")
                get("/playlist_action?action=add&playlist=$created&url=$VIDEO&$CLIP")
                get("/playlist_action?action=add&playlist=$created&url=https%3A%2F%2Fexample.com%2F&$CLIP")
                get("/playlist_action?action=add&playlist=missing&url=$VIDEO")
                get("/playlist_action?action=create&name=")
                get("/api/v1/library")
                get("/playlist_action?action=delete&playlist=$created")
                get("/api/v1/mylist?id=$created")
            }
        }

    @Test
    fun `history and settings`() =
        serverScenario {
            record("history-settings") {
                get("/history_action?action=clear")
                get("/api/v1/history")
                HistoryDbHelper
                    .getInstance(ApplicationProvider.getApplicationContext())
                    .saveToHistory("Me at the zoo", VIDEO, "jawed", "http://t/z.jpg", 0, CHANNEL, null)
                get("/api/v1/history")
                get("/handoff?id=$VIDEO&t=12.5&d=100&title=Me+at+the+zoo")
                get("/handoff?id=https%3A%2F%2Fexample.com%2F")
                get("/api/v1/history")
                get("/api/v1/watch_progress?id=$VIDEO&percent=50&durationSeconds=19")
                get("/api/v1/watch_progress?id=$VIDEO&percent=x")
                get("/api/v1/history")
                get("/history_action?action=remove&url=$VIDEO")
                get("/api/v1/history")
                get("/api/v1/settings")
                get("/api/v1/settings?hideWatched=true&hideShorts=true")
                get("/api/v1/settings?hideWatched=false&hideShorts=false")
                get("/search-history")
                get("/search-history?delete=none")
            }
        }

    @Test
    fun remote() =
        serverScenario {
            record("remote") {
                get("/release-lock?release_code=none")
                val lock = JSONObject(get("/send-link?id=__connect_only__&title=Baseline")).getString("release_code")
                mask(lock, "<lock>")
                get("/send-link?id=__connect_only__")
                get("/remote-state?release_code=none&watching=1")
                get(
                    "/remote-state?release_code=$lock&watching=1&title=Baseline&t=3.5&d=19&paused=0&vol=0.5&svcs=0:YouTube,5:Bilibili&svc=0",
                )
                get("/release-lock?release_code=$lock")
                val next = JSONObject(get("/send-link?id=__connect_only__")).getString("release_code")
                mask(next, "<lock>")
                get("/release-lock?release_code=$next")
            }
        }

    private companion object {
        // Offline-answerable; others need app assets/Hilt or network.
        val WITHOUT_PARAMETERS =
            listOf(
                "/api/v1/cast/devices",
                "/api/v1/cast/start",
                "/api/v1/cast/stop",
                "/api/v1/channel",
                "/api/v1/comments",
                "/api/v1/download",
                "/api/v1/feed",
                "/api/v1/history",
                "/api/v1/library",
                "/api/v1/liked",
                "/api/v1/mylist",
                "/api/v1/playlist",
                "/api/v1/playlists",
                "/api/v1/search",
                "/api/v1/settings",
                "/api/v1/state",
                "/api/v1/video",
                "/api/v1/watch_progress",
                "/block_channel",
                "/bookmark_playlist",
                "/danmaku",
                "/downloadfile",
                "/handoff",
                "/history_action",
                "/hls",
                "/hlsvod",
                "/image-proxy",
                "/live_chat",
                "/pair",
                "/play",
                "/playlist_action",
                "/rate_video",
                "/release-lock",
                "/remote-state",
                "/search-history",
                "/send-link",
                "/stream",
                "/subscribe",
                "/subtitles",
                "/thumbnails",
                "/watch_later_action",
            )
    }
}
