package io.github.aedev.flow.localserver

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.localserver.remote.DISLIKE
import io.github.aedev.flow.localserver.remote.LIKE
import io.github.aedev.flow.localserver.remote.danmakuCommand
import io.github.aedev.flow.localserver.remote.hasSettings
import io.github.aedev.flow.localserver.remote.qualityCommand
import io.github.aedev.flow.localserver.remote.rateCommand
import io.github.aedev.flow.localserver.remote.sendRemoteCommand
import io.github.aedev.flow.localserver.remote.volumeCommand
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
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
    fun `a live room with danmaku has a setting to offer`() {
        assertThat(hasSettings(RemoteState(durationSec = 0.0, danmaku = true))).isTrue()
    }

    @Test
    fun `a held choice ignores the page's report until the hold ends`() {
        var clock = 1_000L
        val hold = RemoteChoiceHold { clock }

        assertThat(hold.chosenQuality("auto", "auto")).isEqualTo("auto")

        hold.hold(RemoteChoice.QUALITY)
        assertThat(hold.chosenQuality("auto", "4")).isEqualTo("4")

        clock += CHOICE_HOLD_MS + 1
        assertThat(hold.chosenQuality("4", "4")).isEqualTo("4")
        assertThat(hold.chosenQuality("auto", "4")).isEqualTo("auto")
    }

    @Test
    fun `the speed is held the same way and falls back to normal`() {
        var clock = 0L
        val hold = RemoteChoiceHold { clock }

        hold.hold(RemoteChoice.RATE)
        assertThat(hold.rate("1.0", 1.5f)).isEqualTo(1.5f)

        clock += CHOICE_HOLD_MS + 1
        assertThat(hold.rate("1.25", 1.5f)).isEqualTo(1.25f)
        assertThat(hold.rate("junk", 1.5f)).isEqualTo(1f)
    }

    @Test
    fun `danmaku is held like a choice and absent when the page has none`() {
        var clock = 0L
        val hold = RemoteChoiceHold { clock }

        assertThat(hold.danmaku(null, true)).isNull()
        assertThat(hold.danmaku("1", null)).isTrue()
        assertThat(hold.danmaku("0", true)).isFalse()

        hold.hold(RemoteChoice.DANMAKU)
        assertThat(hold.danmaku("1", false)).isFalse()
        assertThat(hold.danmaku(null, false)).isNull()

        clock += CHOICE_HOLD_MS + 1
        assertThat(hold.danmaku("1", false)).isTrue()
    }

    @Test
    fun `the commands are the words remote js handles`() {
        assertThat(qualityCommand("h4")).isEqualTo("quality:h4")
        assertThat(rateCommand(1.5f)).isEqualTo("rate:1.5")
        assertThat(danmakuCommand(true)).isEqualTo("danmaku:1")
        assertThat(danmakuCommand(false)).isEqualTo("danmaku:0")
        assertThat(volumeCommand(-0.1f)).isEqualTo("volume:-0.1")
    }

    @Test
    fun `a rating is held like a choice, absent when the page says none and read from its words`() {
        var clock = 0L
        val hold = RemoteChoiceHold { clock }

        assertThat(hold.rating(null, RemoteRating.LIKED)).isNull()
        assertThat(hold.rating("like", null)).isEqualTo(RemoteRating.LIKED)
        assertThat(hold.rating("dislike", null)).isEqualTo(RemoteRating.DISLIKED)
        assertThat(hold.rating("none", null)).isEqualTo(RemoteRating.NONE)

        hold.hold(RemoteChoice.RATING)
        assertThat(hold.rating("none", RemoteRating.LIKED)).isEqualTo(RemoteRating.LIKED)

        clock += CHOICE_HOLD_MS + 1
        assertThat(hold.rating("none", RemoteRating.LIKED)).isEqualTo(RemoteRating.NONE)
    }

    @Test
    fun `pressing like twice takes it back and dislike after like replaces it`() {
        RemoteSession.updateRemoteState(RemoteState(watching = true, rating = RemoteRating.NONE))

        sendRemoteCommand(LIKE)
        assertThat(RemoteSession.remoteState.value.rating).isEqualTo(RemoteRating.LIKED)

        sendRemoteCommand(DISLIKE)
        assertThat(RemoteSession.remoteState.value.rating).isEqualTo(RemoteRating.DISLIKED)

        sendRemoteCommand(DISLIKE)
        assertThat(RemoteSession.remoteState.value.rating).isEqualTo(RemoteRating.NONE)

        RemoteSession.getAndClearPendingCommands()
        RemoteSession.updateRemoteState(RemoteState())
    }
}
