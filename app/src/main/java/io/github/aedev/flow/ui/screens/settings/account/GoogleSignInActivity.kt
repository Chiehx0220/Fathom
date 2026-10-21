package io.github.aedev.flow.ui.screens.settings.account

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.github.aedev.flow.R
import io.github.aedev.flow.data.account.GoogleAccount
import io.github.aedev.flow.data.account.GoogleAccountStore
import io.github.aedev.flow.data.account.GoogleCookie
import io.github.aedev.flow.data.local.AppFontPreferences
import io.github.aedev.flow.data.local.LocalDataManager
import io.github.aedev.flow.ui.startup.FlowTheme
import io.github.aedev.flow.ui.startup.themeSettings
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class GoogleSignInActivity : ComponentActivity() {
    @Inject
    lateinit var store: GoogleAccountStore

    @Inject
    lateinit var appFontPreferences: AppFontPreferences

    private var saved = false

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val theme = LocalDataManager(applicationContext).themeSettings(appFontPreferences)
        setContent {
            val settings = theme.collectAsState(initial = null).value ?: return@setContent
            FlowTheme(settings) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.google_account_sign_in)) },
                            navigationIcon = {
                                IconButton(onClick = ::finish) {
                                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.close))
                                }
                            },
                        )
                    },
                ) { padding ->
                    AndroidView(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        factory = ::createWebView,
                        onRelease = WebView::destroy,
                    )
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(context: Context) =
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient =
                object : WebViewClient() {
                    override fun onPageFinished(
                        view: WebView,
                        url: String?,
                    ) {
                        if (saved || !GoogleCookie.isYouTubeHost(url?.let { Uri.parse(it).host })) return
                        val cookie = CookieManager.getInstance().getCookie(YOUTUBE_ORIGIN)
                        if (!GoogleCookie.isSignedIn(cookie)) return
                        saved = true
                        view.evaluateJavascript(DATA_SYNC_ID_SCRIPT) { finishSignIn(cookie, GoogleCookie.dataSyncId(it)) }
                    }
                }
            loadUrl(SIGN_IN_URL)
        }

    private fun finishSignIn(
        cookie: String,
        dataSyncId: String?,
    ) {
        lifecycleScope.launch {
            store.save(GoogleAccount(cookie, dataSyncId))
            // The attestation WebView reads the same cookie jar and must stay anonymous.
            CookieManager.getInstance().run {
                removeAllCookies(null)
                flush()
            }
            finish()
        }
    }

    private companion object {
        const val YOUTUBE_ORIGIN = "https://www.youtube.com"
        const val SIGN_IN_URL = "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fwww.youtube.com%2F"
        const val DATA_SYNC_ID_SCRIPT = "(function(){try{return ytcfg.get('DATASYNC_ID')||''}catch(e){return ''}})()"
    }
}
