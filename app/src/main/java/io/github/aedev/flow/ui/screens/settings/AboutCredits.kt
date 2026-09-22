package io.github.aedev.flow.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.ui.res.stringResource
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

/** One row per project the Bilibili client and the local web server were built from, each linking to it. */
internal fun LazyListScope.aboutCreditItems(context: Context) {
    CREDITS.forEach { credit ->
        item { AboutRowDivider() }
        item {
            AboutRow(
                icon = Icons.Outlined.Extension,
                title = credit.name,
                subtitle = stringResource(credit.roleRes),
                onClick = { openUrl(context, credit.url) },
            )
        }
    }
}
