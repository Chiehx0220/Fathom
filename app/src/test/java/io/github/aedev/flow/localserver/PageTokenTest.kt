package io.github.aedev.flow.localserver

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.schabi.newpipe.extractor.Page
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.util.Base64

class PageTokenTest {
    @Test
    fun `every field survives the round trip`() {
        val page = Page("https://yt/next", "id1", listOf("a", "b"), mapOf("k" to "v"), byteArrayOf(0, 1, 127, -1))

        val back = PageToken.decode(PageToken.encode(page))!!

        assertThat(back.url).isEqualTo("https://yt/next")
        assertThat(back.id).isEqualTo("id1")
        assertThat(back.ids).containsExactly("a", "b").inOrder()
        assertThat(back.cookies).containsExactly("k", "v")
        assertThat(back.body).isEqualTo(byteArrayOf(0, 1, 127, -1))
    }

    @Test
    fun `absent fields stay absent`() {
        val back = PageToken.decode(PageToken.encode(Page("c:12")))!!

        assertThat(back.url).isEqualTo("c:12")
        assertThat(back.id).isNull()
        assertThat(back.ids).isNull()
        assertThat(back.cookies).isNull()
        assertThat(back.body).isNull()
    }

    @Test
    fun `no page is no token`() {
        assertThat(PageToken.encode(null)).isNull()
        assertThat(PageToken.decode(null)).isNull()
        assertThat(PageToken.decode("")).isNull()
    }

    @Test
    fun `a java-serialized object is never read`() {
        val bytes =
            ByteArrayOutputStream()
                .also {
                    ObjectOutputStream(it).use { out ->
                        out.writeObject(Page("https://yt/next"))
                    }
                }.toByteArray()

        assertThat(PageToken.decode(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes))).isNull()
    }

    @Test
    fun `garbage and oversized tokens are ignored`() {
        assertThat(PageToken.decode("not a token!")).isNull()
        assertThat(PageToken.decode(Base64.getUrlEncoder().encodeToString("[1]".toByteArray()))).isNull()
        assertThat(PageToken.decode("A".repeat(70_000))).isNull()
    }
}
