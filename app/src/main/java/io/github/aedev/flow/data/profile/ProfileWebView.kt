package io.github.aedev.flow.data.profile

import android.content.Context
import android.os.Build
import android.util.Log
import android.webkit.WebView

/**
 * WebView keeps cookies and storage per data folder, and they carry the YouTube visitor identity BotGuard grades,
 * so each profile has its own on Android 9 and later. Android 8 has one folder, which a switch clears instead.
 */
object ProfileWebView {
    private const val TAG = "ProfileWebView"

    val clearedAtSwitch: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.P

    /** Must run before any WebView exists in the process. Default keeps app_webview; another profile gets app_webview_<id>. */
    fun applySuffix(context: Context) {
        if (clearedAtSwitch) return
        val profile = ActiveProfile.get(context)
        if (profile.isDefault) return
        runCatching { WebView.setDataDirectorySuffix(profile.id) }
            .onFailure { Log.e(TAG, "WebView already in use; it shares the default folder in this process", it) }
    }
}
