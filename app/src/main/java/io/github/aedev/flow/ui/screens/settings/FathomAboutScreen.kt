package io.github.aedev.flow.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FathomAboutScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDonations: () -> Unit,
    onNavigateToFathomDonations: () -> Unit,
) {
    val context = LocalContext.current
    var showLicenseDialog by remember { mutableStateOf(false) }
    var showDeviceInfoDialog by remember { mutableStateOf(false) }
    var showChangelogDialog by remember { mutableStateOf(false) }
    var upstreamOpen by rememberSaveable { mutableStateOf(false) }
    // version info
    val packageInfo =
        remember {
            try {
                context.packageManager.getPackageInfo(context.packageName, 0)
            } catch (e: Exception) {
                null
            }
        }
    val versionName = packageInfo?.versionName ?: context.getString(R.string.unknown)
    val versionCode =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo?.longVersionCode?.toString() ?: "0"
        } else {
            @Suppress("DEPRECATION")
            packageInfo?.versionCode?.toString() ?: "0"
        }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            FlowTopBar(
                title = stringResource(R.string.about_title),
                onBack = onNavigateBack,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_fathom_logo),
                        contentDescription = stringResource(R.string.app_logo_desc),
                        modifier = Modifier.size(72.dp),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(id = R.string.app_name),
                        style =
                            MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 3.sp,
                            ),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.v_version_template, versionName, versionCode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { HorizontalDivider() }

            item { AboutSectionLabel(stringResource(R.string.about_section_fathom)) }
            item {
                FathomAboutRow(
                    icon = painterResource(id = R.drawable.ic_github),
                    title = stringResource(R.string.github_label),
                    subtitle = stringResource(R.string.github_subtitle),
                    onClick = { openExternalUrl(context, AboutLinks.FATHOM_GITHUB) },
                )
            }
            item { FathomAboutDivider() }
            item {
                FathomAboutRow(
                    icon = Icons.Outlined.VolunteerActivism,
                    title = stringResource(R.string.donate_item_title),
                    subtitle = stringResource(R.string.support_dev_subtitle),
                    onClick = onNavigateToFathomDonations,
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(top = 8.dp)) }

            item {
                FathomAboutRow(
                    icon = Icons.Outlined.Person,
                    title = stringResource(R.string.about_upstream_title),
                    subtitle = stringResource(R.string.about_upstream_subtitle),
                    onClick = { upstreamOpen = !upstreamOpen },
                    expanded = upstreamOpen,
                )
            }
            if (upstreamOpen) {
                item { AboutChildRow(stringResource(R.string.about_based_on), "github.com/A-EDev/Flow") { openExternalUrl(context, AboutLinks.FLOW_GITHUB) } }
                item { AboutChildRow(stringResource(R.string.about_changelog), stringResource(R.string.whats_new_in_flow)) { showChangelogDialog = true } }
                item { AboutChildRow(stringResource(R.string.about_website), stringResource(R.string.about_website_address)) { openExternalUrl(context, AboutLinks.FLOW_WEBSITE) } }
                item { AboutChildRow(stringResource(R.string.about_reddit), stringResource(R.string.about_reddit_subtitle)) { openExternalUrl(context, AboutLinks.FLOW_REDDIT) } }
                item { AboutChildRow(stringResource(R.string.donate_item_title), stringResource(R.string.support_dev_subtitle), onNavigateToDonations) }
            }
            item { HorizontalDivider(modifier = Modifier.padding(top = 8.dp)) }

            item { AboutSectionLabel(stringResource(R.string.section_legal)) }
            item {
                FathomAboutRow(
                    icon = Icons.Outlined.Description,
                    title = stringResource(R.string.about_license),
                    subtitle = stringResource(R.string.about_license_name),
                    onClick = { showLicenseDialog = true },
                )
            }
            item { FathomAboutDivider() }
            item {
                FathomAboutRow(
                    icon = Icons.Outlined.Extension,
                    title = stringResource(R.string.newpipe_extractor_title),
                    subtitle = stringResource(R.string.newpipe_extractor_subtitle),
                    onClick = { openExternalUrl(context, AboutLinks.NEWPIPE_EXTRACTOR) },
                )
            }
            aboutCreditItems(context)
            item { HorizontalDivider(modifier = Modifier.padding(top = 8.dp)) }

            item { AboutSectionLabel(stringResource(R.string.section_device)) }
            item {
                FathomAboutRow(
                    icon = Icons.Outlined.Smartphone,
                    title = stringResource(R.string.about_device_info),
                    subtitle = "${Build.MANUFACTURER} ${Build.MODEL}",
                    onClick = { showDeviceInfoDialog = true },
                )
            }
        }
    }

    if (showLicenseDialog) LicenseDialog(onDismiss = { showLicenseDialog = false })
    if (showDeviceInfoDialog) DeviceInfoDialog(onDismiss = { showDeviceInfoDialog = false })
    if (showChangelogDialog) ChangelogDialog(onDismiss = { showChangelogDialog = false })
}

@Composable
private fun AboutSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
internal fun FathomAboutRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    expanded: Boolean? = null,
) = FathomAboutRow(rememberVectorPainter(icon), title, subtitle, onClick, expanded)

@Composable
internal fun FathomAboutRow(
    icon: Painter,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    expanded: Boolean? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded != null) {
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AboutChildRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 62.dp, end = 24.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun FathomAboutDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 62.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

internal fun openExternalUrl(
    context: Context,
    url: String,
) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
