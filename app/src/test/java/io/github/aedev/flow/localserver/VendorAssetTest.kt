package io.github.aedev.flow.localserver

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VendorAssetTest {
    @Test
    fun `a file of the vendor folder maps to its asset`() {
        assertThat(vendorAssetPath("/vendor/videojs/video.js")).isEqualTo("web/vendor/videojs/video.js")
        assertThat(vendorAssetPath("/vendor/videojs/media/dash-video.js")).isEqualTo("web/vendor/videojs/media/dash-video.js")
        assertThat(vendorAssetPath("/vendor/videojs/adapter-4Vl80rTB.js")).isEqualTo("web/vendor/videojs/adapter-4Vl80rTB.js")
    }

    @Test
    fun `nothing that could leave the vendor folder is served`() {
        for (path in listOf(
            "/vendor/../app/js/util.js",
            "/vendor/videojs/../../app/index.html",
            "/vendor/./x.js",
            "/vendor/%2e%2e/app/index.html",
            "/vendor//x.js",
            "/vendor/",
            "/vendor",
            "/vendor/a b.js",
            "/vendor/a\\b.js",
            "/other/videojs/video.js",
            "/app/js/util.js",
        )) {
            assertThat(vendorAssetPath(path)).isNull()
        }
    }

    @Test
    fun `scripts and styles carry the types a browser needs to run them`() {
        assertThat(vendorContentType("/vendor/videojs/video.js")).isEqualTo("text/javascript; charset=UTF-8")
        assertThat(vendorContentType("/vendor/videojs/global.css")).isEqualTo("text/css; charset=UTF-8")
        assertThat(vendorContentType("/vendor/videojs/package.json")).isEqualTo("application/json; charset=UTF-8")
    }

    @Test
    fun `a type the server cannot send as text is not served`() {
        assertThat(vendorContentType("/vendor/x.wasm")).isNull()
        assertThat(vendorContentType("/vendor/x.png")).isNull()
        assertThat(vendorContentType("/vendor/noextension")).isNull()
    }
}
