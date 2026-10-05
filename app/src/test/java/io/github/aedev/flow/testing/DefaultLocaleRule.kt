package io.github.aedev.flow.testing

import org.junit.rules.ExternalResource
import java.util.Locale

/**
 * Pins [Locale.getDefault] for a test and puts the previous one back after it. Language names and
 * their sort order follow the default locale, so a test that expects English ones would otherwise
 * pass or fail with the language of whatever machine runs it.
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
