package io.github.aedev.flow.data.local

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExportedPreferencesTest {
    private val quality = stringPreferencesKey("default_quality_wifi")
    private val autoplay = booleanPreferencesKey("autoplay_enabled")
    private val deviceNames = setOf(quality.name)

    private fun stored(vararg entries: Pair<Preferences.Key<*>, Any>) = mapOf(*entries)

    @Test
    fun `a copy leaves the profile store's stale device keys behind`() {
        val exported = exportedPreferences(stored(Pair(quality, "480p"), Pair(autoplay, true)), emptyMap(), deviceNames)

        assertThat(exported).containsExactly(autoplay, true)
    }

    @Test
    fun `a full backup takes device keys from the device store only`() {
        val exported = exportedPreferences(stored(Pair(quality, "480p"), Pair(autoplay, true)), stored(Pair(quality, "1080p")), deviceNames)

        assertThat(exported).containsExactly(autoplay, true, quality, "1080p")
    }
}
