package io.github.aedev.flow.localserver

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class PairingPageTest {
    private val page = File("src/main/assets/web/pair/index.html").readText()

    @Test
    fun `the page asks the pair route for the code`() {
        assertThat(page).contains("/pair?code=")
        assertThat(PAIR_PATH).isEqualTo("/pair")
    }

    @Test
    fun `the page needs nothing from another address`() {
        assertThat(Regex("""(src|href)\s*=\s*["']?(https?:)?//""").containsMatchIn(page)).isFalse()
        assertThat(Regex("""url\(\s*["']?(https?:)?//""").containsMatchIn(page)).isFalse()
        assertThat(page).doesNotContain("@import")
    }

    @Test
    fun `the page has English and Traditional Chinese text`() {
        assertThat(page).contains("Enter the pairing code shown on your phone")
        assertThat(page).contains("輸入手機上顯示的配對碼")
    }

    @Test
    fun `the page takes no server-side placeholder`() {
        assertThat(page).doesNotContain("{{")
    }
}
