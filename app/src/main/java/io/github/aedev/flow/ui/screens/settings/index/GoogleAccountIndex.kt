package io.github.aedev.flow.ui.screens.settings.index

import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.settings.SettingEntry
import io.github.aedev.flow.ui.components.settings.SettingsDestination

internal object GoogleAccountIndex {
    private fun entry(
        key: String,
        title: Int,
        summary: Int,
    ) = SettingEntry(
        key = "google_account.$key",
        title = title,
        summary = summary,
        keywords = R.string.settings_keywords_google_account,
        section = R.string.google_account_title,
        destination = SettingsDestination.GOOGLE_ACCOUNT,
    )

    val signIn = entry("sign_in", R.string.google_account_sign_in, R.string.google_account_sign_in_summary)
    val signOut = entry("sign_out", R.string.google_account_sign_out, R.string.google_account_sign_out_summary)

    val entries = listOf(signIn, signOut)
}
