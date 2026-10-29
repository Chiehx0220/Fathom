package io.github.aedev.flow.ui.screens.settings.profiles

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProfileNameTest {
    private val others = listOf("Default", "Study")

    @Test
    fun `a name is required`() {
        assertThat(nameIssue("", others)).isEqualTo(NameIssue.BLANK)
        assertThat(nameIssue("   ", others)).isEqualTo(NameIssue.BLANK)
    }

    @Test
    fun `two profiles never share a name, whatever the case or spacing`() {
        assertThat(nameIssue("study", others)).isEqualTo(NameIssue.TAKEN)
        assertThat(nameIssue(" Default ", others)).isEqualTo(NameIssue.TAKEN)
        assertThat(nameIssue("Gaming", others)).isNull()
    }
}
