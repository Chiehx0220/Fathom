package io.github.aedev.flow.ui.screens.channel

import androidx.annotation.StringRes
import io.github.aedev.flow.R

/**
 * The channel screen's tabs, addressed by identity rather than by position.
 *
 * [ChannelUiState.selectedTab] persists the ordinal, so the order here is part of the saved state —
 * append new tabs at the end. [visible] is what the pager actually renders, which is a subset once
 * the Shorts master switch is off.
 */
enum class ChannelTab(
    @StringRes val titleRes: Int,
) {
    Videos(R.string.tab_videos),
    Shorts(R.string.tab_shorts),
    Live(R.string.tab_live),
    Playlists(R.string.tab_playlists),
    Posts(R.string.tab_posts),
    About(R.string.tab_about),
    ;

    companion object {
        fun from(ordinal: Int): ChannelTab = entries.getOrElse(ordinal) { Videos }

        /**
         * Live and Posts are YouTube-only concepts in this app - the generic extractor's
         * [org.schabi.newpipe.extractor.linkhandler.ChannelTabs] has no equivalent for either, and
         * Posts is backed entirely by Flow's own YouTube-InnerTube client. Hiding them for other
         * services avoids a tab that can only ever show empty/spinner.
         */
        fun visible(
            shortsEnabled: Boolean,
            isYouTube: Boolean = true,
        ): List<ChannelTab> =
            entries.filterNot { tab ->
                (tab == Shorts && (!shortsEnabled || !isYouTube)) ||
                    ((tab == Live || tab == Posts) && !isYouTube)
            }
    }
}
