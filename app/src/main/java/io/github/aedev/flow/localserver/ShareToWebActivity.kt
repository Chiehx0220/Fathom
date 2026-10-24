package io.github.aedev.flow.localserver

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import dagger.hilt.android.AndroidEntryPoint
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.AppFontPreferences
import io.github.aedev.flow.data.local.LocalDataManager
import io.github.aedev.flow.localserver.remote.EnableServerAndSend
import io.github.aedev.flow.localserver.remote.WaitingForPageDialog
import io.github.aedev.flow.localserver.remote.queueVideoForPage
import io.github.aedev.flow.localserver.remote.sharedVideoUrl
import io.github.aedev.flow.ui.linkTextOf
import io.github.aedev.flow.ui.startup.FlowTheme
import io.github.aedev.flow.ui.startup.themeSettings
import javax.inject.Inject

/** Share target: paired → send; server on → hold; server off → confirm. */
@AndroidEntryPoint
class ShareToWebActivity : ComponentActivity() {
    @Inject
    lateinit var appFontPreferences: AppFontPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = sharedVideoUrl(linkTextOf(intent))
        if (url == null) {
            finishWithToast(R.string.share_web_unsupported)
            return
        }
        val running = ServerService.isRunning
        if (running) RemoteSession.isPageAlive() // releases a stale lock
        if (running && savedInstanceState == null && queueVideoForPage(url)) {
            finishWithToast(R.string.share_web_sent)
            return
        }

        val theme = LocalDataManager(applicationContext).themeSettings(appFontPreferences)
        setContent {
            val settings = theme.collectAsState(initial = null).value ?: return@setContent
            FlowTheme(settings) {
                if (running) {
                    WaitingForPageDialog(onDismiss = ::finish)
                } else {
                    EnableServerAndSend(send = { queueVideoForPage(url) }, onCancel = ::finish, onDone = ::finish)
                }
            }
        }
    }

    private fun finishWithToast(message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }
}
