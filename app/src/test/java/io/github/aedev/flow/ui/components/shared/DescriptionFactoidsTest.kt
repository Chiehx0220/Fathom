package io.github.aedev.flow.ui.components.shared

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DescriptionFactoidsTest {
    private val now =
        LocalDate
            .of(2026, 10, 28)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private fun dayOf(read: DescriptionDate): LocalDate =
        java.time.Instant
            .ofEpochMilli((read as DescriptionDate.OnDay).timestampMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()

    @Test
    fun `a premiere with a date reads as that day`() {
        val read = readDescriptionDate("Premiered Oct 24, 2026", 0L, false, now, "en")

        assertThat(dayOf(read)).isEqualTo(LocalDate.of(2026, 10, 24))
    }

    @Test
    fun `a past stream with a date reads as that day`() {
        val read = readDescriptionDate("Streamed live on Jul 12, 2022", 0L, false, now, "en")

        assertThat(dayOf(read)).isEqualTo(LocalDate.of(2022, 7, 12))
    }

    @Test
    fun `a plain upload date reads as that day`() {
        val read = readDescriptionDate("Oct 24, 2009", 0L, false, now, "en")

        assertThat(dayOf(read)).isEqualTo(LocalDate.of(2009, 10, 24))
    }

    @Test
    fun `a premiere known only by age keeps the age and the kind apart`() {
        val read = readDescriptionDate("Premiered 2 days ago", 0L, false, now, "en")

        assertThat(read).isEqualTo(DescriptionDate.Aged("2 days ago", DescriptionDateKind.PREMIERED))
    }

    @Test
    fun `a stream known only by age is a stream`() {
        val read = readDescriptionDate("Streamed 3 weeks ago", 0L, false, now, "en")

        assertThat(read).isEqualTo(DescriptionDate.Aged("3 weeks ago", DescriptionDateKind.STREAMED))
    }

    @Test
    fun `an upload known only by age is an upload`() {
        val read = readDescriptionDate("3 years ago", 0L, false, now, "en")

        assertThat(read).isEqualTo(DescriptionDate.Aged("3 years ago", DescriptionDateKind.UPLOADED))
    }

    @Test
    fun `an exact timestamp places the day even behind an age`() {
        val stamp = now - 2 * 24 * 60 * 60 * 1000L

        val read = readDescriptionDate("Premiered 2 days ago", stamp, true, now, "en")

        assertThat(read).isEqualTo(DescriptionDate.OnDay(stamp))
    }

    @Test
    fun `text nothing can read stays as it came`() {
        val read = readDescriptionDate("sometime", 0L, false, now, "en")

        assertThat(read).isEqualTo(DescriptionDate.Aged("sometime", DescriptionDateKind.UPLOADED))
    }
}
