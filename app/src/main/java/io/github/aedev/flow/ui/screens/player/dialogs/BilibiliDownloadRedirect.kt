package io.github.aedev.flow.ui.screens.player.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.shared.quickactions.sharedQuickActionsViewModel

// PlayerDialogsContainer hook: Bilibili streams not in player InnerTube state; card menu loader offers them.
@Composable
internal fun BilibiliDownloadRedirect(
    video: Video,
    onDismiss: () -> Unit,
) {
    val quickActions = sharedQuickActionsViewModel()
    LaunchedEffect(video.id) {
        quickActions.requestDownload(video)
        onDismiss()
    }
}
