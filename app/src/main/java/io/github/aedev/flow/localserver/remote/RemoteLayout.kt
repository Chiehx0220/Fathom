package io.github.aedev.flow.localserver.remote

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/** Gap between component groups. */
internal val RemoteGroupSpacing = 16.dp

/** Top inset 4dp: [RemoteStatusStrip] chips add a 48dp minimum touch target. Bottom inset clears the mode bar. */
internal val RemotePagePadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 24.dp)
