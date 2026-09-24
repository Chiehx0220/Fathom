package io.github.aedev.flow.ui.screens.settings

import io.github.aedev.flow.R

internal data class Credit(
    val name: String,
    val roleRes: Int,
    val url: String,
)

// What this fork's own features are built on. NewPipe Extractor, which YouTube runs on, is listed by the About screen itself.
internal val CREDITS =
    listOf(
        Credit("PipePipeExtractor", R.string.about_credit_pipepipe, "https://github.com/InfinityLoop1308/PipePipeExtractor"),
        Credit("localtube", R.string.about_credit_localtube, "https://github.com/diekaiju/localtube"),
        Credit("Vidstack", R.string.about_credit_vidstack, "https://vidstack.io"),
        Credit("dash.js", R.string.about_credit_dashjs, "https://github.com/Dash-Industry-Forum/dash.js"),
    )
