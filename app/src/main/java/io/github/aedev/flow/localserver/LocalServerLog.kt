package io.github.aedev.flow.localserver

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

internal fun serverLog(message: String) {
    android.util.Log.d("LocalServer", message)
}

/** Blocking [block]; failure logged as "[what] failed", then [fallback]. Cancellation propagates. */
internal inline fun <T> blockingOrElse(
    what: String,
    fallback: (Exception) -> T,
    crossinline block: suspend () -> T,
): T =
    try {
        runBlocking { block() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        serverLog("$what failed: ${e.message}")
        fallback(e)
    }
