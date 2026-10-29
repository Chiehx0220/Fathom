package io.github.aedev.flow.ui

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import io.github.aedev.flow.BilibiliLinks
import io.github.aedev.flow.R
import io.github.aedev.flow.data.playlist.PlaylistImport
import io.github.aedev.flow.data.playlist.PlaylistTransfer
import io.github.aedev.flow.data.profile.SwitchIntentQueue
import io.github.aedev.flow.notification.NotificationHelper
import io.github.aedev.flow.player.GlobalPlayerState
import io.github.aedev.flow.ui.components.library.message
import io.github.aedev.flow.ui.screens.update.UPDATE_ROUTE
import io.github.aedev.flow.utils.PLAYLIST_FILE_MIME_TYPE
import kotlinx.coroutines.launch

/** What MainActivity is started or re-delivered with: a link, a widget or notification route, a share or a playlist file. */
internal class IncomingIntents(
    private val activity: ComponentActivity,
    private val links: IncomingLinks,
    private val playlistTransfer: dagger.Lazy<PlaylistTransfer>,
) {
    private fun playlistFileIn(intent: Intent): Uri? {
        val type =
            intent.type ?: intent.data?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT }?.let(activity.contentResolver::getType)
        if (type != PLAYLIST_FILE_MIME_TYPE) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }
    }

    private fun importPlaylistFile(file: Uri) {
        activity.lifecycleScope.launch {
            val result = playlistTransfer.get().import(file, activity.getString(R.string.imported_playlist_default_name))
            Toast.makeText(activity, result.message(activity), Toast.LENGTH_LONG).show()
            if (result is PlaylistImport.Imported) {
                links.openRoute(if (result.isMusic) musicCollectionRoute(result.playlistId) else "playlist/${result.playlistId}")
            }
        }
    }

    /** [isRestoringState]: a recreated activity gets its launch intent again; a playlist file in it was already imported. */
    fun handle(
        intent: Intent,
        isRestoringState: Boolean,
    ) {
        if (SwitchIntentQueue.holdIfSwitching(activity, intent, playlistFileIn(intent))) return
        if (!NotificationHelper.opensInRunningProfile(activity, intent)) {
            intent.replaceExtras(Bundle())
            return
        }
        if (intent.getBooleanExtra(NotificationHelper.EXTRA_OPEN_UPDATE, false)) {
            intent.removeExtra(NotificationHelper.EXTRA_OPEN_UPDATE)
            links.openRoute(UPDATE_ROUTE)
            return
        }
        val playlistFile = SwitchIntentQueue.heldPlaylist(activity, intent) ?: playlistFileIn(intent)
        if (playlistFile != null) {
            if (!isRestoringState) importPlaylistFile(playlistFile)
            return
        }
        val notificationVideoId = intent.getStringExtra("notification_video_id") ?: intent.getStringExtra("video_id")

        val widgetRoute =
            intent.getStringExtra(
                io.github.aedev.flow.widget.core.action.WidgetDeepLink.EXTRA_WIDGET_ROUTE,
            )
        if (widgetRoute != null) {
            intent.removeExtra(io.github.aedev.flow.widget.core.action.WidgetDeepLink.EXTRA_WIDGET_ROUTE)
            links.openRoute(widgetRoute)
            return
        }

        val linkText = linkTextOf(intent)
        if (linkText != null && intent.component?.className?.endsWith(DOWNLOAD_SHARE_ALIAS) == true) {
            if (!isRestoringState && !links.openDownload(linkText)) {
                Toast.makeText(activity, R.string.share_download_not_a_video, Toast.LENGTH_LONG).show()
            }
            return
        }
        if (linkText != null) {
            val understood =
                BilibiliLinks.open(linkText, activity.lifecycleScope, links) ||
                    links.openLink(linkText, isShared = intent.action == Intent.ACTION_SEND, isRestoring = isRestoringState)
            if (!understood && !isRestoringState) Toast.makeText(activity, R.string.link_not_supported, Toast.LENGTH_LONG).show()
            return
        }

        if (intent.getBooleanExtra("open_music_player", false)) {
            links.openMusicPlayer()
            intent.removeExtra("notification_video_id")
            intent.removeExtra("video_id")
            return
        }

        if (intent.getBooleanExtra("open_video_player", false)) {
            intent.removeExtra("open_video_player")
            val currentVideoId = GlobalPlayerState.currentVideo.value?.id
            if (currentVideoId != null) links.openVideo(currentVideoId)
            return
        }

        val isShort = intent.getBooleanExtra("is_short", false) || intent.getBooleanExtra("is_shorts", false)
        notificationVideoId?.let { links.openVideo(it, isShort = isShort) }
    }

    private companion object {
        /** The share-menu entry Download with Flow: an activity-alias of MainActivity in the manifest. */
        const val DOWNLOAD_SHARE_ALIAS = ".DownloadShareAlias"
    }
}
