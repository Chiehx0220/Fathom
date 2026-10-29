package io.github.aedev.flow.data.video.downloader.collection

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.entity.DownloadCollectionEntity
import io.github.aedev.flow.data.local.entity.DownloadCollectionKind
import io.github.aedev.flow.data.profile.RunningProfile
import io.github.aedev.flow.data.video.downloader.collection.CollectionOwners.asPage
import org.junit.Test

class CollectionOwnersTest {
    private val default = RunningProfile(id = "d", isDefault = true)
    private val study = RunningProfile(id = "7f3c", isDefault = false)
    private val gaming = RunningProfile(id = "9a1b", isDefault = false)

    private fun collection(
        id: String,
        kind: DownloadCollectionKind,
    ) = DownloadCollectionEntity(id = id, kind = kind, title = "t", folderName = id)

    @Test
    fun `Default keeps bare ids and another profile prefixes only its own playlists`() {
        assertThat(CollectionOwners.storedId("watch_later", ownPlaylist = true, default)).isEqualTo("watch_later")
        assertThat(CollectionOwners.storedId("watch_later", ownPlaylist = true, study)).isEqualTo("7f3c:watch_later")
        assertThat(CollectionOwners.storedId("PLabc", ownPlaylist = false, study)).isEqualTo("PLabc")
    }

    @Test
    fun `a page finds its collection under the playlist id it opens with`() {
        val stored = CollectionOwners.storedId("watch_later", ownPlaylist = true, study)

        assertThat(CollectionOwners.candidates("watch_later", study)).contains(stored)
        assertThat(CollectionOwners.playlistId(stored)).isEqualTo("watch_later")
        assertThat(collection(stored, DownloadCollectionKind.LOCAL_PLAYLIST).asPage().id).isEqualTo("watch_later")
        assertThat(CollectionOwners.playlistId("MPREb_x")).isEqualTo("MPREb_x")
    }

    @Test
    fun `each profile sees its own playlists and the shared ones, never another's`() {
        val defaultsWatchLater = collection("watch_later", DownloadCollectionKind.LOCAL_PLAYLIST)
        val studysWatchLater = collection("7f3c:watch_later", DownloadCollectionKind.LOCAL_PLAYLIST)
        val studysMix = collection("7f3c:0b9e2c4d-1111-4c2e-9a0f-2d5b8e4c1a77", DownloadCollectionKind.MUSIC_PLAYLIST)
        val album = collection("MPREb_x", DownloadCollectionKind.MUSIC_ALBUM)

        assertThat(listOf(defaultsWatchLater, studysWatchLater, studysMix, album).filter { CollectionOwners.isVisible(it, default) })
            .containsExactly(defaultsWatchLater, album)
        assertThat(listOf(defaultsWatchLater, studysWatchLater, studysMix, album).filter { CollectionOwners.isVisible(it, study) })
            .containsExactly(studysWatchLater, studysMix, album)
        assertThat(listOf(defaultsWatchLater, studysWatchLater, studysMix, album).filter { CollectionOwners.isVisible(it, gaming) })
            .containsExactly(album)
    }

    @Test
    fun `a deleted profile's collections are exactly those under its prefix`() {
        val prefix = CollectionOwners.prefixOf(study.id)

        assertThat(CollectionOwners.storedId("watch_later", ownPlaylist = true, study)).startsWith(prefix)
        assertThat(CollectionOwners.storedId("watch_later", ownPlaylist = true, gaming)).doesNotContain(prefix)
        assertThat(CollectionOwners.storedId("watch_later", ownPlaylist = true, default)).doesNotContain(prefix)
    }
}
