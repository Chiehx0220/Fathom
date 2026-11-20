package io.github.aedev.flow.localserver

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MseCodecsTest {
    @Test
    fun `AV1 tier letter is upper-cased`() {
        assertThat("av01.0.08m.08".forMse()).isEqualTo("av01.0.08M.08")
        assertThat("av01.0.12h.10".forMse()).isEqualTo("av01.0.12H.10")
        assertThat("av01.0.08M.08".forMse()).isEqualTo("av01.0.08M.08")
    }

    @Test
    fun `other codecs and malformed AV1 strings are left alone`() {
        assertThat("avc1.640028".forMse()).isEqualTo("avc1.640028")
        assertThat("vp09.00.40.08".forMse()).isEqualTo("vp09.00.40.08")
        assertThat("mp4a.40.2".forMse()).isEqualTo("mp4a.40.2")
        assertThat("av01".forMse()).isEqualTo("av01")
        assertThat("av01.0.8.08".forMse()).isEqualTo("av01.0.8.08")
        assertThat("".forMse()).isEmpty()
    }
}
