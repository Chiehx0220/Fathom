package io.github.aedev.flow.ui.screens.settings.account

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.settings.SettingsPage
import io.github.aedev.flow.ui.components.settings.nav
import io.github.aedev.flow.ui.screens.settings.index.GoogleAccountIndex

@Composable
internal fun GoogleAccountScreen(
    onBack: (() -> Unit)?,
    highlight: String?,
    viewModel: GoogleAccountViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val info by viewModel.info.collectAsStateWithLifecycle()
    val signedInLabel = stringResource(R.string.google_account_signed_in)
    val signedInAs = info?.let { stringResource(R.string.google_account_signed_in_as, it.email ?: it.name) }

    SettingsPage(
        title = stringResource(R.string.google_account_title),
        onBack = onBack,
        highlight = highlight,
    ) {
        group(key = "google_account.session", header = R.string.google_account_title) {
            if (!signedIn) {
                nav(
                    GoogleAccountIndex.signIn,
                    icon = Icons.Outlined.Login,
                    showChevron = false,
                    onClick = { context.startActivity(Intent(context, GoogleSignInActivity::class.java)) },
                )
            } else {
                nav(
                    GoogleAccountIndex.signOut,
                    value = signedInAs ?: signedInLabel,
                    icon = Icons.Outlined.Logout,
                    showChevron = false,
                    onClick = viewModel::signOut,
                )
            }
        }
    }
}
