package io.github.aedev.flow.localserver

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.aedev.flow.data.local.LocalDataManager
import io.github.aedev.flow.localserver.remote.RemoteScreen
import io.github.aedev.flow.localserver.remote.VOLUME_STEP
import io.github.aedev.flow.ui.theme.FlowTheme
import io.github.aedev.flow.ui.theme.ThemeMode
import io.github.aedev.flow.ui.theme.ThemeVariant

/**
 * The phone as a remote for the page that paired with the local server (its cast button). Three ways to drive it:
 * a direction pad for browsing, playback keys for a playing video, and a touchpad for anything small. Commands go
 * straight to the server, which hands them to the paired page; the page reports back how it is playing.
 * The screens are in the `remote` package.
 */
class RemoteActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A remote that dims while you hold it is a remote that stops working.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val dataManager = LocalDataManager(applicationContext)
        setContent {
            val themeMode by dataManager.themeMode.collectAsState(initial = ThemeMode.MATERIAL_YOU)
            val themeVariant by dataManager.themeVariant.collectAsState(initial = ThemeVariant.DARK)
            FlowTheme(themeMode = themeMode, themeVariant = themeVariant) {
                RemoteScreen(onBack = { finish() })
            }
        }
    }
    // The phone's own volume keys turn the page's volume while the remote is paired.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val step =
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> VOLUME_STEP
                KeyEvent.KEYCODE_VOLUME_DOWN -> -VOLUME_STEP
                else -> return super.onKeyDown(keyCode, event)
            }
        if (!LocalHttpServer.remoteLock.value.locked) return super.onKeyDown(keyCode, event)
        LocalHttpServer.addPendingCommand("volume:$step")
        return true
    }
}
