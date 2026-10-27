package io.github.aedev.flow.localserver

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import io.github.aedev.flow.data.local.AppFontPreferences
import io.github.aedev.flow.data.local.LocalDataManager
import io.github.aedev.flow.localserver.remote.RemoteScreen
import io.github.aedev.flow.localserver.remote.VOLUME_STEP
import io.github.aedev.flow.localserver.remote.volumeCommand
import io.github.aedev.flow.ui.startup.FlowTheme
import io.github.aedev.flow.ui.startup.themeSettings
import javax.inject.Inject

/**
 * Phone as remote: direction pad, playback keys, touchpad. Command → server → page; page reports state.
 */
@AndroidEntryPoint
class RemoteActivity : ComponentActivity() {
    @Inject
    lateinit var appFontPreferences: AppFontPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dimming remote stops working.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val theme = LocalDataManager(applicationContext).themeSettings(appFontPreferences)
        setContent {
            val settings = theme.collectAsState(initial = null).value ?: return@setContent
            FlowTheme(settings) {
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
        RemoteSession.broadcast(volumeCommand(step))
        return true
    }
}
