package io.github.aedev.flow.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.settings.SettingsGroupScope
import io.github.aedev.flow.ui.components.settings.SettingsPage
import io.github.aedev.flow.ui.components.shared.FlowNavRow
import io.github.aedev.flow.ui.screens.settings.about.ChangelogSheet
import io.github.aedev.flow.ui.screens.settings.about.DeviceInfoDialog
import io.github.aedev.flow.ui.screens.settings.about.IconReddit

private enum class FathomAboutDialog { CHANGELOG, DEVICE }

private const val FATHOM_CREATOR_NAME = "Chiehx0220"
private const val FATHOM_CREATOR_URL = "https://github.com/Chiehx0220"
private const val FATHOM_CREATOR_AVATAR_URL = "https://github.com/Chiehx0220.png?size=144"
private const val FLOW_CREATOR_AVATAR_URL = "https://github.com/A-EDev.png?size=144"
private const val FATHOM_REPO_NAME = "Chiehx0220/Fathom"

private val AvatarSize = 48.dp
private val LogoSize = 72.dp
private val HeaderPadding = 24.dp
private val HeaderSpacing = 8.dp

/** Fathom's About page, laid out like the upstream one but with Fathom's own project and credits. */
@Composable
internal fun FathomAboutScreen(onBack: (() -> Unit)?) {
    val uriHandler = LocalUriHandler.current
    var dialog by rememberSaveable { mutableStateOf<FathomAboutDialog?>(null) }
    val open = { url: String -> runCatching { uriHandler.openUri(url) }; Unit }

    SettingsPage(
        title = stringResource(R.string.settings_item_about_flow),
        onBack = onBack,
    ) {
        item("about.header") { FathomAboutHeader() }
        group(key = "about.creators") {
            row("about.creator.fathom") { shape ->
                CreatorRow(
                    shape = shape,
                    overline = stringResource(R.string.about_fathom_maintained_by),
                    name = FATHOM_CREATOR_NAME,
                    role = stringResource(R.string.about_fathom_maintainer_role),
                    avatarUrl = FATHOM_CREATOR_AVATAR_URL,
                    onClick = { open(FATHOM_CREATOR_URL) },
                )
            }
            row("about.creator.flow") { shape ->
                CreatorRow(
                    shape = shape,
                    overline = stringResource(R.string.about_based_on),
                    name = stringResource(R.string.about_creator_name),
                    role = stringResource(R.string.settings_creator_role),
                    avatarUrl = FLOW_CREATOR_AVATAR_URL,
                    onClick = { open(AboutLinks.FLOW_GITHUB) },
                )
            }
        }
        group(key = "about.app", header = R.string.section_app) {
            link(
                key = "about.app.changelog",
                title = { stringResource(R.string.about_changelog) },
                subtitle = { stringResource(R.string.whats_new_in_flow) },
                icon = Icons.Outlined.History,
                onClick = { dialog = FathomAboutDialog.CHANGELOG },
            )
        }
        group(key = "about.contact", header = R.string.section_contact) {
            link(
                key = "about.contact.website",
                title = { stringResource(R.string.about_contact_flow_website) },
                subtitle = { stringResource(R.string.about_website_address) },
                icon = Icons.Outlined.Public,
                onClick = { open(AboutLinks.FLOW_WEBSITE) },
            )
            link(
                key = "about.contact.github",
                title = { stringResource(R.string.about_contact_fathom_github) },
                subtitle = { FATHOM_REPO_NAME },
                iconRes = R.drawable.ic_github,
                onClick = { open(AboutLinks.FATHOM_GITHUB) },
            )
            link(
                key = "about.contact.reddit",
                title = { stringResource(R.string.about_contact_flow_reddit) },
                subtitle = { stringResource(R.string.about_reddit_subtitle) },
                icon = IconReddit,
                onClick = { open(AboutLinks.FLOW_REDDIT) },
            )
        }
        group(key = "about.legal", header = R.string.section_legal) {
            link(
                key = "about.legal.license",
                title = { stringResource(R.string.about_license) },
                subtitle = { stringResource(R.string.about_license_name) },
                icon = Icons.Outlined.Description,
                onClick = { open(AboutLinks.LICENSE) },
            )
            link(
                key = "about.legal.newpipe",
                title = { stringResource(R.string.newpipe_extractor_title) },
                subtitle = { stringResource(R.string.newpipe_extractor_subtitle) },
                icon = Icons.Outlined.Extension,
                onClick = { open(AboutLinks.NEWPIPE_EXTRACTOR) },
            )
            CREDITS.forEach { credit ->
                link(
                    key = "about.legal.${credit.name}",
                    title = { credit.name },
                    subtitle = { stringResource(credit.roleRes) },
                    icon = Icons.Outlined.Extension,
                    onClick = { open(credit.url) },
                )
            }
        }
        group(key = "about.device", header = R.string.section_device) {
            link(
                key = "about.device.info",
                title = { stringResource(R.string.about_device_info) },
                subtitle = { "${Build.MANUFACTURER} ${Build.MODEL}" },
                icon = Icons.Outlined.Smartphone,
                onClick = { dialog = FathomAboutDialog.DEVICE },
            )
        }
    }

    when (dialog) {
        FathomAboutDialog.CHANGELOG -> ChangelogSheet(onDismiss = { dialog = null })
        FathomAboutDialog.DEVICE -> DeviceInfoDialog(onDismiss = { dialog = null })
        null -> Unit
    }
}

/** One row of the page: a title, a line under it, a leading icon, no chevron. */
private fun SettingsGroupScope.link(
    key: String,
    title: @Composable () -> String,
    subtitle: @Composable () -> String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    iconRes: Int? = null,
) = row(key) { shape ->
    FlowNavRow(
        title = title(),
        supportingText = subtitle(),
        onClick = onClick,
        showChevron = false,
        leadingIcon = icon,
        leadingPainter = iconRes?.let { painterResource(it) },
        shape = shape,
    )
}

@Composable
private fun FathomAboutHeader() {
    val context = LocalContext.current
    val unknown = stringResource(R.string.unknown)
    val version =
        remember {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }
                .getOrNull()
                ?.let { it.versionName to it.longVersionCode.toString() }
        }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = HeaderPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HeaderSpacing),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_fathom_logo),
            contentDescription = stringResource(R.string.app_logo_desc),
            modifier = Modifier.size(LogoSize),
        )
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.v_version_template, version?.first ?: unknown, version?.second ?: "0"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A person's card at the top of the page: avatar, what they are to the project, their name and role. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CreatorRow(
    shape: Shape,
    overline: String,
    name: String,
    role: String,
    avatarUrl: String,
    onClick: () -> Unit,
) {
    var avatarLoaded by remember { mutableStateOf(false) }
    SegmentedListItem(
        onClick = onClick,
        verticalAlignment = Alignment.CenterVertically,
        shapes = ListItemDefaults.shapes(shape = shape),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        leadingContent = {
            Box(
                modifier = Modifier.size(AvatarSize).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (!avatarLoaded) {
                    Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onSuccess = { avatarLoaded = true },
                    modifier = Modifier.matchParentSize(),
                )
            }
        },
        overlineContent = { Text(overline) },
        supportingContent = { Text(role) },
        trailingContent = { Icon(painterResource(R.drawable.ic_github), contentDescription = null) },
    ) {
        Text(name, style = MaterialTheme.typography.titleMedium)
    }
}
