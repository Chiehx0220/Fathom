package io.github.aedev.flow.data.profile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.aedev.flow.data.local.ChannelSubscription
import io.github.aedev.flow.data.local.LikedVideoInfo
import io.github.aedev.flow.data.local.LikedVideosRepository
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.local.SearchHistoryRepository
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.notes.NoteKind
import io.github.aedev.flow.data.notes.NotesRepository
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.recommendation.InteractionType
import io.github.aedev.flow.notification.UpcomingReminder
import io.github.aedev.flow.notification.UpcomingVideoReminderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Debug builds only: the hook `scripts/profiles/leak_matrix.sh` drives over adb. It writes a marker into every
 * profile-bound store of the running profile, creates profiles and switches between them; the script then looks for
 * each marker in files that belong to any other profile.
 *
 * `--es op mark --es tag zzleakA` · `--es op create --es name A [--ez copySettings true]` · `--es op switch --es id <id>`
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ProfileProbeEntryPoint {
    fun profiles(): ProfileRepository

    fun switcher(): ProfileSwitcher

    fun searches(): SearchHistoryRepository

    fun subscriptions(): SubscriptionRepository

    fun playlists(): PlaylistRepository

    fun history(): ViewHistory

    fun likes(): LikedVideosRepository

    fun notes(): NotesRepository

    fun preferences(): PlayerPreferences

    fun engine(): FlowNeuroEngine
}

class ProfileProbeReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val app = EntryPointAccessors.fromApplication(context, ProfileProbeEntryPoint::class.java)
        val pending = goAsync()
        scope.launch {
            val result = runCatching { app.handle(context, intent) }.getOrElse { "error ${it.javaClass.simpleName}: ${it.message}" }
            Log.i(TAG, "${intent.getStringExtra("op")} in ${app.profiles().running().id}: $result")
            pending.finish()
        }
    }

    private suspend fun ProfileProbeEntryPoint.handle(
        context: Context,
        intent: Intent,
    ): String =
        when (intent.getStringExtra("op")) {
            "mark" -> {
                mark(context, requireNotNull(intent.getStringExtra("tag")))
            }

            "create" -> {
                val copy = if (intent.getBooleanExtra("copySettings", false)) setOf(SlicePart.SETTINGS) else emptySet()
                when (val created = profiles().create(requireNotNull(intent.getStringExtra("name")), copy = copy)) {
                    is CreateResult.Created -> "created ${created.profile.id}"
                    else -> created.toString()
                }
            }

            "switch" -> {
                val id = requireNotNull(intent.getStringExtra("id"))
                withContext(Dispatchers.Main) { switcher().switchTo(id) }.toString()
            }

            else -> {
                "unknown op"
            }
        }

    /** [tag] in every profile-bound store; the subtitle language gets `zzset…` so a settings-only copy can be told apart. */
    private suspend fun ProfileProbeEntryPoint.mark(
        context: Context,
        tag: String,
    ): String {
        val videoId = "${tag}vid"
        val title = "$tag video"
        val channel = "$tag channel"
        searches().saveSearchQuery(tag)
        subscriptions().subscribe(ChannelSubscription(channelId = "UC$tag", channelName = channel, channelThumbnail = ""))
        playlists().createPlaylist(UUID.randomUUID().toString(), "$tag playlist", "", isPrivate = true)
        history().savePlaybackPosition(videoId, position = 30_000, duration = 60_000, title = title, channelName = channel)
        likes().likeVideo(LikedVideoInfo(videoId = videoId, title = title, thumbnail = "", channelName = channel))
        notes().save(NoteKind.Video, videoId, "$tag note")
        engine().onVideoInteraction(
            Video(videoId, title, channel, "UC$tag", "", duration = 600, viewCount = 1_000, uploadDate = ""),
            InteractionType.WATCHED,
            percentWatched = 0.9f,
        )
        preferences().setPreferredSubtitleLanguage(tag.replace("zzleak", "zzset"))
        val releaseMs = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7)
        preferences().setUpcomingVideoReminder(videoId, true)
        preferences().saveUpcomingReminder(UpcomingReminder(videoId, releaseMs, title, channel, null))
        UpcomingVideoReminderWorker.scheduleReminder(context, videoId, releaseMs, title, channel, null)
        return "marked $tag"
    }

    private companion object {
        const val TAG = "ProfileProbe"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
