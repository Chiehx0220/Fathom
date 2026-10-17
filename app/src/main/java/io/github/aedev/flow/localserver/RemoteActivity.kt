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
import io.github.aedev.flow.localserver.remote.volumeCommand
import io.github.aedev.flow.ui.theme.FlowTheme
import io.github.aedev.flow.ui.theme.ThemeMode
import io.github.aedev.flow.ui.theme.ThemeVariant

/**
 * Phone as remote for the page paired with the local server (cast button). Modes: direction pad (browsing), playback keys (playing video),
 * touchpad. Commands → server → paired page; page reports playback state. Screens: `remote` package.
 */
class RemoteActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dimming remote stops working.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val dataManager = LocalDataManager(applicationContext)
        setContent {
            val themeMode by dataManager.themeMode.collectAsState(initial = ThemeMode.NORDIC_HORIZON)
            val themeVariant by dataManager.themeVariant.collectAsState(initial = ThemeVariant.DARK)
            FlowTheme(themeMode = themeMode, themeVariant = themeVariant) {
                RemoteScreen(onBack = { finish() })
            }
        }
    }

    // Phone volume keys → page volume while paired.
    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        val step =
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> VOLUME_STEP
                KeyEvent.KEYCODE_VOLUME_DOWN -> -VOLUME_STEP
                else -> return super.onKeyDown(keyCode, event)
            }
        if (!RemoteSession.remoteLock.value.locked) return super.onKeyDown(keyCode, event)
        RemoteSession.addPendingCommand(volumeCommand(step))
        return true
    }
}
