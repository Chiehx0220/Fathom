package io.github.aedev.flow.ui.screens.settings

import androidx.annotation.StringRes
import io.github.aedev.flow.R

/** Every external address the About and donation screens link to, shared by the phone and TV versions. */
internal object AboutLinks {
    const val FATHOM_GITHUB = "https://github.com/Chiehx0220/Fathom"
    const val FLOW_GITHUB = "https://github.com/A-EDev/Flow"
    const val FLOW_RELEASES = "https://github.com/A-EDev/Flow/releases"
    const val FLOW_WEBSITE = "https://flow.aedev.me"
    const val FLOW_REDDIT = "https://www.reddit.com/r/Flow_Official/"
    const val FLOW_DONATION = "https://patreon.com/A_EDev"
    const val LICENSE = "https://www.gnu.org/licenses/gpl-3.0.html"
    const val NEWPIPE_EXTRACTOR = "https://github.com/TeamNewPipe/NewPipeExtractor"
}

internal data class DonationLink(
    val name: String,
    @StringRes val buttonLabel: Int,
    val url: String,
) {
    val address: String get() = url.removePrefix("https://")
}

/** Fathom's own ways to donate, main one first. */
internal val FATHOM_DONATIONS =
    listOf(
        DonationLink("GitHub Sponsors", R.string.support_fathom_sponsors, "https://github.com/sponsors/Chiehx0220"),
        DonationLink("Ko-fi", R.string.support_fathom_kofi, "https://ko-fi.com/chiehx0220"),
    )
