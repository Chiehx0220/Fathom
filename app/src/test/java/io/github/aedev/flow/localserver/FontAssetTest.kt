package io.github.aedev.flow.localserver

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FontAssetTest {
    @Test
    fun `a woff2 file of the fonts folder maps to its asset`() {
        assertThat(fontAssetPath("/fonts/roboto-400.425c0713.woff2")).isEqualTo("web/app/fonts/roboto-400.425c0713.woff2")
    }

    @Test
    fun `nothing but a plain woff2 name is served`() {
        for (path in listOf(
            "/fonts/../js/util.js",
            "/fonts/..%2fjs/util.js",
            "/fonts/a/b.woff2",
            "/fonts/x.woff",
            "/fonts/x.js",
            "/fonts/.woff2.js",
            "/fonts/",
            "/fonts",
            "/fonts/a b.woff2",
            "/other/x.woff2",
        )) {
            assertThat(fontAssetPath(path)).isNull()
        }
    }
}
