package io.github.aedev.flow.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import io.github.aedev.flow.ui.screens.settings.about.DonationsScreen

/** Support page: Fathom, then upstream Flow; each with own donation way. */
@Composable
fun FathomDonationsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showFlow by rememberSaveable { mutableStateOf(false) }
    if (showFlow) {
        BackHandler { showFlow = false }
        DonationsScreen(onNavigateBack = { showFlow = false })
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            FlowTopBar(
                title = stringResource(R.string.support_donations_title),
                onBack = onNavigateBack,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            item { FathomSupportSection() }
            item { HorizontalDivider() }
            item { UpstreamSupportSection(onOpen = { showFlow = true }) }
            item {
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = stringResource(R.string.thank_you_support_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Fathom's links; the first is the primary action. */
@Composable
private fun FathomSupportSection() {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 24.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.VolunteerActivism,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(52.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        SupportHeading(R.string.support_fathom_title, R.string.support_fathom_desc, MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FATHOM_DONATIONS.forEachIndexed { index, link ->
                SupportButton(
                    label = stringResource(link.buttonLabel),
                    onClick = { uriHandler.openUri(link.url) },
                    primary = index == 0,
                )
            }
        }
    }
}

@Composable
private fun UpstreamSupportSection(onOpen: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 28.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SupportHeading(R.string.support_upstream_title, R.string.support_upstream_desc, MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))
        SupportButton(label = stringResource(R.string.support_upstream_button), onClick = onOpen, primary = false)
    }
}

@Composable
private fun SupportHeading(
    @StringRes title: Int,
    @StringRes description: Int,
    titleStyle: TextStyle,
) {
    Text(
        text = stringResource(title),
        style = titleStyle,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SupportButton(
    label: String,
    onClick: () -> Unit,
    primary: Boolean,
) {
    val modifier = Modifier.fillMaxWidth().height(50.dp)
    val shape = RoundedCornerShape(8.dp)
    val content: @Composable RowScope.() -> Unit = { Text(text = label, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
    if (primary) {
        Button(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.background,
                ),
            content = content,
        )
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, shape = shape, content = content)
    }
}
