package io.github.aedev.flow.ui.tv.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.BuildConfig
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.screens.settings.AboutLinks
import io.github.aedev.flow.ui.screens.settings.CREDITS
import io.github.aedev.flow.ui.screens.settings.FATHOM_DONATIONS
import io.github.aedev.flow.ui.tv.components.TvNavRow
import io.github.aedev.flow.ui.tv.components.TvSectionHeader
import io.github.aedev.flow.ui.tv.focus.ProvideTvColumnPivot


/** TV counterpart of the mobile About screen, adapted for remote focus and scrolling. */
@Composable
fun TvFathomAboutPane(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var upstreamOpen by rememberSaveable { mutableStateOf(false) }
    var fathomDonateOpen by rememberSaveable { mutableStateOf(false) }

    ProvideTvColumnPivot {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "identity") {
                TvAboutIdentity()
            }

            item(key = "fathom-header") {
                TvAboutSectionHeader(stringResource(R.string.about_section_fathom))
            }
            item(key = "github") {
                TvNavRow(
                    label = stringResource(R.string.github_label),
                    supportingText = stringResource(R.string.github_subtitle),
                    leadingIcon = Icons.Outlined.Code,
                    onClick = { context.openUrl(AboutLinks.FATHOM_GITHUB) },
                )
            }
            item(key = "fathom-donate") {
                TvNavRow(
                    label = stringResource(R.string.donate_item_title),
                    supportingText = stringResource(R.string.support_dev_subtitle),
                    value = if (fathomDonateOpen) "▴" else "▾",
                    leadingIcon = Icons.Outlined.VolunteerActivism,
                    onClick = { fathomDonateOpen = !fathomDonateOpen },
                )
            }
            if (fathomDonateOpen) {
                FATHOM_DONATIONS.forEach { link ->
                    item(key = "fathom-donate-${link.name}") { TvChildRow(link.name, link.address) { context.openUrl(link.url) } }
                }
            }

            item(key = "upstream") {
                TvNavRow(
                    label = stringResource(R.string.about_upstream_title),
                    supportingText = stringResource(R.string.about_upstream_subtitle),
                    value = if (upstreamOpen) "▴" else "▾",
                    leadingIcon = Icons.Outlined.Person,
                    onClick = { upstreamOpen = !upstreamOpen },
                )
            }
            if (upstreamOpen) {
                item(key = "creator") { TvChildRow(stringResource(R.string.about_based_on), "github.com/A-EDev/Flow") { context.openUrl(AboutLinks.FLOW_GITHUB) } }
                item(key = "changelog") { TvChildRow(stringResource(R.string.about_changelog), stringResource(R.string.whats_new_in_flow)) { context.openUrl(AboutLinks.FLOW_RELEASES) } }
                item(key = "website") { TvChildRow(stringResource(R.string.about_website), stringResource(R.string.about_website_address)) { context.openUrl(AboutLinks.FLOW_WEBSITE) } }
                item(key = "reddit") { TvChildRow(stringResource(R.string.about_reddit), stringResource(R.string.about_reddit_subtitle)) { context.openUrl(AboutLinks.FLOW_REDDIT) } }
                item(key = "donate") { TvChildRow(stringResource(R.string.donate_item_title), stringResource(R.string.support_dev_subtitle)) { context.openUrl(AboutLinks.FLOW_DONATION) } }
            }

            item(key = "legal-header") {
                TvAboutSectionHeader(stringResource(R.string.section_legal))
            }
            item(key = "license") {
                TvNavRow(
                    label = stringResource(R.string.about_license),
                    value = stringResource(R.string.about_license_name),
                    leadingIcon = Icons.Outlined.Description,
                    onClick = { context.openUrl(AboutLinks.LICENSE) },
                )
            }
            item(key = "newpipe") {
                TvNavRow(
                    label = stringResource(R.string.newpipe_extractor_title),
                    supportingText = stringResource(R.string.newpipe_extractor_subtitle),
                    leadingIcon = Icons.Outlined.Extension,
                    onClick = { context.openUrl(AboutLinks.NEWPIPE_EXTRACTOR) },
                )
            }
            CREDITS.forEach { credit ->
                item(key = "credit-${credit.name}") {
                    TvNavRow(
                        label = credit.name,
                        supportingText = stringResource(credit.roleRes),
                        leadingIcon = Icons.Outlined.Extension,
                        onClick = { context.openUrl(credit.url) },
                    )
                }
            }

            item(key = "device-header") {
                TvAboutSectionHeader(stringResource(R.string.section_device))
            }
            item(key = "device-info") {
                TvNavRow(
                    label = stringResource(R.string.about_device_info),
                    value = "${Build.MANUFACTURER} ${Build.MODEL}",
                    leadingIcon = Icons.Outlined.Tv,
                    onClick = { context.openDeviceInfo() },
                )
            }
        }
    }
}

@Composable
private fun TvChildRow(label: String, value: String, onClick: () -> Unit) {
    TvNavRow(
        label = label,
        value = value,
        modifier = Modifier.padding(start = 32.dp),
        onClick = onClick,
    )
}

@Composable
private fun TvAboutIdentity() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_fathom_logo),
                contentDescription = stringResource(R.string.app_logo_desc),
                modifier = Modifier.size(64.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(
                        R.string.v_version_template,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE.toString(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TvAboutSectionHeader(title: String) {
    TvSectionHeader(
        title = title,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

private fun Context.openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

private fun Context.openDeviceInfo() {
    val intent = Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}
