package io.github.aedev.flow.localserver

import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import io.github.aedev.flow.data.model.Video as FlowVideo

// Native use case: same learning signal, history and feed invalidation as the phone.
private fun HistoryDbHelper.feedback() = localServerEntryPoint(appContext).videoFeedback()

internal fun HistoryDbHelper.markNotInterested(video: FlowVideo) = runBlocking { feedback().markNotInterested(video) }

internal fun HistoryDbHelper.markWatched(video: FlowVideo) = runBlocking { feedback().markWatched(video) }

internal fun HistoryDbHelper.markUnwatched(video: FlowVideo) = runBlocking { feedback().markUnwatched(video) }

/** Runs [action]; false, logged, when injection or the use case fails. */
private suspend fun attempt(
    what: String,
    action: suspend () -> Unit,
): Boolean =
    try {
        action()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        serverLog("$what through the feedback use case failed: ${e.message}")
        false
    }

/** Native block (`UC` ids) also keeps the name, counts the action, refreshes feeds; other ids, or a failure, only join the engine's block list. */
internal fun HistoryDbHelper.blockChannel(
    channelUrl: String,
    name: String,
) {
    val channelId = channelUrlToId(channelUrl) ?: return
    runBlocking {
        val native =
            channelId.startsWith("UC") &&
                attempt("Block") { feedback().blockChannel(buildFlowVideoFromParams("", "", name, null, channelUrl)) }
        if (!native) {
            ensureFlowNeuroInitialized()
            FlowNeuroEngine.blockChannel(channelId)
        }
    }
}

internal fun HistoryDbHelper.unblockChannel(channelUrl: String) {
    val channelId = channelUrlToId(channelUrl) ?: return
    runBlocking {
        if (!attempt("Unblock") { feedback().unblockChannel(channelId) }) {
            ensureFlowNeuroInitialized()
            FlowNeuroEngine.unblockChannel(channelId)
        }
    }
}
