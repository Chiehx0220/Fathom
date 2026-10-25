package io.github.aedev.flow.localserver

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.PlaylistInfo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class PlaylistHandlersTest {
    private val mix = PlaylistInfo("1", "Mix", "", 3, "http://t/1.jpg", true, 0L)
    private val study = PlaylistInfo("2", "Study", "", 0, "", true, 0L)

    @Test
    fun `a picker row says whether the playlist already holds the video`() {
        val rows = playlistChoices(listOf(mix, study), setOf("2"))
        assertThat(rows[0].contains).isFalse()
        assertThat(rows[1].contains).isTrue()
    }

    @Test
    fun `library cards are marked local so they open in the app`() {
        val card = mix.toCard()
        assertThat(card.isLocal).isTrue()
        assertThat(card.id).isEqualTo("1")
        assertThat(card.videoCount).isEqualTo(3)
    }

    @Test
    fun `playlist changes are same-origin only`() {
        assertThat(MUTATING_PATHS).contains("/playlist_action")
    }
}
