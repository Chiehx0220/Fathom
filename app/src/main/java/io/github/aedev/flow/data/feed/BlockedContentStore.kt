package io.github.aedev.flow.data.feed

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.safePreferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.blockedContentDataStore by safePreferencesDataStore(name = "blocked_content")

/** Device-local blocked channels and not-interested videos; videos are capped at [MAX_SUPPRESSED_VIDEOS], oldest dropped. */
@Singleton
class BlockedContentStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val channelsKey = stringSetPreferencesKey("blocked_channels")
        private val videosKey = stringPreferencesKey("suppressed_videos")

        val blockedChannelIds: Flow<Set<String>> = context.blockedContentDataStore.data.map { it[channelsKey].orEmpty() }

        val suppressedVideoIds: Flow<Set<String>> = context.blockedContentDataStore.data.map { it.videos().toSet() }

        val exclusions: Flow<FeedExclusions> =
            context.blockedContentDataStore.data.map { FeedExclusions(it.videos().toSet(), it[channelsKey].orEmpty()) }

        suspend fun exclusions(): FeedExclusions = exclusions.first()

        suspend fun blockChannel(channelId: String) {
            if (channelId.isBlank()) return
            context.blockedContentDataStore.edit { it[channelsKey] = it[channelsKey].orEmpty() + channelId }
        }

        suspend fun unblockChannel(channelId: String) {
            context.blockedContentDataStore.edit { it[channelsKey] = it[channelsKey].orEmpty() - channelId }
        }

        suspend fun markNotInterested(videoId: String) {
            if (videoId.isBlank()) return
            context.blockedContentDataStore.edit { prefs ->
                val updated = (prefs.videos() - videoId) + videoId
                prefs[videosKey] = updated.takeLast(MAX_SUPPRESSED_VIDEOS).joinToString(SEPARATOR)
            }
        }

        suspend fun unmarkNotInterested(videoId: String) {
            context.blockedContentDataStore.edit { prefs -> prefs[videosKey] = (prefs.videos() - videoId).joinToString(SEPARATOR) }
        }

        private fun Preferences.videos(): List<String> = this[videosKey]?.split(SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty()

        companion object {
            const val MAX_SUPPRESSED_VIDEOS = 2_000
            private const val SEPARATOR = "\n"
        }
    }
