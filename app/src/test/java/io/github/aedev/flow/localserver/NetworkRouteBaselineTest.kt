package io.github.aedev.flow.localserver

import android.app.Application
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

private const val VIDEO = "https://www.youtube.com/watch?v=jNQXAC9IVRw"
private const val CHANNEL = "https://www.youtube.com/channel/UCchan"

/** Network-backed routes over [FakeSource]: params → source calls → JSON. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class)
class NetworkRouteBaselineTest {
    @Test
    fun `listing routes`() {
        val source = FakeSource()
        serverScenario(source) {
            record("network-listings") {
                val first = JSONObject(get("/api/v1/search?q=zoo&serviceId=0")).getString("nextPage")
                get("/api/v1/search?q=zoo&serviceId=0&nextPage=$first")
                get("/api/v1/search?q=zoo&serviceId=5")
                get("/api/v1/home?serviceId=0")
                get("/api/v1/home?serviceId=5")
                val chan = JSONObject(get("/api/v1/channel?id=$CHANNEL&tab=playlists&sort=popular")).getString("nextPage")
                get("/api/v1/channel?id=$CHANNEL&nextPage=$chan")
                val list =
                    JSONObject(
                        get("/api/v1/playlist?id=https://www.youtube.com/playlist?list=PLx&serviceId=0"),
                    ).getString("nextPage")
                get("/api/v1/playlist?id=https://www.youtube.com/playlist?list=PLx&nextPage=$list")
                note(source.calls.joinToString("\n"))
            }
        }
    }

    @Test
    fun `watch page routes`() {
        val source = FakeSource()
        serverScenario(source) {
            record("network-watch") {
                get("/history_action?action=clear")
                get("/api/v1/video?id=$VIDEO&serviceId=0")
                get("/api/v1/history")
                val replies = JSONObject(get("/api/v1/comments?id=$VIDEO")).getString("nextPage")
                get("/api/v1/comments?id=$VIDEO&nextPage=$replies")
                get("/api/v1/comments?id=https://www.youtube.com/watch?v=disabled0000")
                get("/api/v1/sponsor?id=$VIDEO&serviceId=0")
                get("/api/v1/sponsor?id=$VIDEO&serviceId=5")
                get("/api/v1/sponsor?serviceId=0")
                get("/danmaku?id=$VIDEO&serviceId=5")
                get("/danmaku?id=$VIDEO&serviceId=0")
                get("/api/v1/watch_progress?id=$VIDEO&percent=40&durationSeconds=19&serviceId=0")
                note(source.calls.joinToString("\n"))
            }
        }
    }
}
