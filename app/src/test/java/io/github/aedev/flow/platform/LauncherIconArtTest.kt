package io.github.aedev.flow.platform

import android.app.Application
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.util.AppIcons
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherIconArtTest {
    private val size = 96

    @Test
    fun `every launcher icon draws a rounded tile with art in the middle`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        AppIcons.ALL.forEach { icon ->
            val tile = LauncherIconArt.render(context, icon, size)
            assertThat(tile.width).isEqualTo(size)
            assertThat(Color.alpha(tile.getPixel(size / 2, size / 2))).isEqualTo(255)
            assertThat(Color.alpha(tile.getPixel(0, 0))).isEqualTo(0)
        }
    }
}
