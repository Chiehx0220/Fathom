package io.github.aedev.flow.data.local

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class DefaultThemeTest {
    @Test
    fun `a fresh install starts on Nordic Horizon`() =
        runBlocking {
            val manager = LocalDataManager(ApplicationProvider.getApplicationContext())
            assertThat(manager.themeMode.first()).isEqualTo(ThemeMode.NORDIC_HORIZON)
        }
}
