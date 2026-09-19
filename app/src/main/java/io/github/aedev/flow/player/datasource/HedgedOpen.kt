package io.github.aedev.flow.player.datasource

import java.io.InterruptedIOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Opens one resource on several mirrors and keeps the first to answer. The first mirror starts alone;
 * the next starts if it is slow past the hedge delay or fails. The others are cancelled once one wins.
 */
internal object HedgedOpen {
    class Winner<T>(
        val value: T,
        val url: String,
        /** True when the winner was not the first mirror tried, i.e. a slow or failed start was covered. */
        val hedged: Boolean,
    )

    private sealed interface Outcome<T> {
        class Opened<T>(val value: T, val url: String) : Outcome<T>

        class Failed<T>(val error: Throwable) : Outcome<T>
    }

    /**
     * @param urls the mirrors, best first.
     * @param open opens one mirror and returns what it opened; may block, may throw.
     * @param close releases something [open] returned but that lost the race.
     * @throws InterruptedIOException if the calling thread is interrupted while waiting, after
     *   cancelling every attempt.
     * @throws Throwable the first failure, when every mirror failed.
     */
    fun <T> race(
        urls: List<String>,
        hedgeDelayMs: Long,
        executor: ExecutorService,
        open: (String) -> T,
        close: (T) -> Unit,
    ): Winner<T> {
        require(urls.isNotEmpty()) { "no mirrors" }
        val outcomes = LinkedBlockingQueue<Outcome<T>>()
        val decided = AtomicBoolean(false)
        val futures = mutableListOf<Future<*>>()
        val firstUrl = urls.first()

        fun launch(url: String) {
            futures +=
                executor.submit {
                    try {
                        val value = open(url)
                        if (decided.compareAndSet(false, true)) {
                            outcomes.put(Outcome.Opened(value, url))
                        } else {
                            close(value)
                        }
                    } catch (e: Throwable) {
                        outcomes.put(Outcome.Failed(e))
                    }
                }
        }

        fun cancelAll() {
            decided.set(true)
            futures.forEach { it.cancel(true) }
            // An attempt can finish between the last poll and now; release what it opened.
            while (true) {
                val late = outcomes.poll() ?: break
                if (late is Outcome.Opened<T>) close(late.value)
            }
        }

        var launched = 0
        var failures = 0
        var firstError: Throwable? = null
        launch(urls[launched++])
        try {
            while (true) {
                val waitMs = if (launched < urls.size) hedgeDelayMs else Long.MAX_VALUE
                when (val outcome = outcomes.poll(waitMs, TimeUnit.MILLISECONDS)) {
                    null -> launch(urls[launched++])

                    is Outcome.Opened<T> -> {
                        futures.forEach { it.cancel(true) }
                        return Winner(outcome.value, outcome.url, hedged = outcome.url != firstUrl)
                    }

                    is Outcome.Failed<T> -> {
                        failures++
                        if (firstError == null) firstError = outcome.error
                        if (launched < urls.size) {
                            launch(urls[launched++])
                        } else if (failures >= launched) {
                            cancelAll()
                            throw firstError!!
                        }
                    }
                }
            }
        } catch (e: InterruptedException) {
            cancelAll()
            Thread.currentThread().interrupt()
            throw InterruptedIOException("interrupted while opening mirrors")
        }
    }
}
