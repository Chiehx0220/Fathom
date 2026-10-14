package io.github.aedev.flow.localserver

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.localserver.remote.hasSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class RemoteQualitiesTest {
    @Test
    fun `a list of id and label pairs keeps its order`() {
        val parsed = parseQualities("""[["h5","1080p60"],["h4","1080p"],["h3","720p"]]""")

        assertThat(parsed)
            .containsExactly(
                RemoteQuality("h5", "1080p60"),
                RemoteQuality("h4", "1080p"),
                RemoteQuality("h3", "720p"),
            ).inOrder()
    }

    @Test
    fun `no list, an empty one and text that is not json are all nothing to choose`() {
        assertThat(parseQualities(null)).isEmpty()
        assertThat(parseQualities("")).isEmpty()
        assertThat(parseQualities("[]")).isEmpty()
        assertThat(parseQualities("not json")).isEmpty()
    }

    @Test
    fun `an entry without an id or a label is left out`() {
        val parsed = parseQualities("""[["h4","1080p"],["","720p"],["h2",""],"text",["h1"]]""")

        assertThat(parsed).containsExactly(RemoteQuality("h4", "1080p"))
    }

    @Test
    fun `a state starts on auto with normal speed`() {
        val state = RemoteState()

        assertThat(state.chosenQuality).isEqualTo("auto")
        assertThat(state.qualities).isEmpty()
        assertThat(state.rate).isEqualTo(1f)
    }

    @Test
    fun `settings are offered for a video with an end or a choice of pictures only`() {
        assertThat(hasSettings(RemoteState(durationSec = 312.0))).isTrue()
        assertThat(hasSettings(RemoteState(qualities = listOf(RemoteQuality("1", "720p"))))).isTrue()
        assertThat(hasSettings(RemoteState(durationSec = 0.0))).isFalse()
    }

    @Test
    fun `a held choice ignores the page's report until the hold ends`() {
        var clock = 1_000L
        val hold = RemoteChoiceHold { clock }

        assertThat(hold.chosenQuality("auto", "auto")).isEqualTo("auto")

        hold.holdQuality()
        assertThat(hold.chosenQuality("auto", "4")).isEqualTo("4")

        clock += CHOICE_HOLD_MS + 1
        assertThat(hold.chosenQuality("4", "4")).isEqualTo("4")
        assertThat(hold.chosenQuality("auto", "4")).isEqualTo("auto")
    }

    @Test
    fun `the speed is held the same way and falls back to normal`() {
        var clock = 0L
        val hold = RemoteChoiceHold { clock }

        hold.holdRate()
        assertThat(hold.rate("1.0", 1.5f)).isEqualTo(1.5f)

        clock += CHOICE_HOLD_MS + 1
        assertThat(hold.rate("1.25", 1.5f)).isEqualTo(1.25f)
        assertThat(hold.rate("junk", 1.5f)).isEqualTo(1f)
    }
}
