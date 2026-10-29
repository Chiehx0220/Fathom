package io.github.aedev.flow.ui.components.profiles

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProfileInitialTest {
    @Test
    fun `the initial is the first character, capitalised`() {
        assertThat(profileInitial("study")).isEqualTo("S")
        assertThat(profileInitial("  gaming ")).isEqualTo("G")
        assertThat(profileInitial("")).isEmpty()
    }

    @Test
    fun `a character made of several code points stays whole`() {
        assertThat(profileInitial("Émilie")).isEqualTo("É")
        assertThat(profileInitial("🇯🇵 Japan")).isEqualTo("🇯🇵")
        assertThat(profileInitial("👩‍💻 work")).isEqualTo("👩‍💻")
    }
}
