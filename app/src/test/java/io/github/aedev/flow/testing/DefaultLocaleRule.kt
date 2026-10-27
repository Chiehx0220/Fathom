package io.github.aedev.flow.testing

import org.junit.rules.ExternalResource
import java.util.Locale

/**
 * Pins [Locale.getDefault] for a test, restores after: language names/sort follow default locale, so tests would otherwise depend on the
 * host machine.
 */
class DefaultLocaleRule(
    private val locale: Locale = Locale.ENGLISH,
) : ExternalResource() {
    private lateinit var previous: Locale

    override fun before() {
        previous = Locale.getDefault()
        Locale.setDefault(locale)
    }

    override fun after() {
        Locale.setDefault(previous)
    }
}
