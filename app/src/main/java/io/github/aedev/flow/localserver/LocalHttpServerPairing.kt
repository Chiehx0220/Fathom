package io.github.aedev.flow.localserver

import java.io.OutputStream

internal const val PAIR_PATH = "/pair"
internal const val ICON_PATH = "/app-icon.png"
private const val PAIRING_REQUIRED = "Unauthorized: pair this browser with the code shown on the phone"
private const val JSON = "application/json; charset=UTF-8"

internal enum class Gate { ADMIT, PAIR_PAGE, REFUSE }

/** Loopback and paired browsers pass; unpaired get the pairing page at `/` and [ICON_PATH]. */
internal fun gateFor(
    path: String,
    paired: Boolean,
    loopback: Boolean,
): Gate =
    when {
        loopback || paired || path == PAIR_PATH || path == ICON_PATH -> Gate.ADMIT
        path == "/" -> Gate.PAIR_PAGE
        else -> Gate.REFUSE
    }

/** False when the request was answered here (pairing page or refusal). */
internal fun RequestContext.admits(
    reply: Reply,
    path: String,
    headers: Map<String, String>,
): Boolean =
    when (gateFor(path, pairing.isPaired(headers["cookie"]), remoteIsLoopback)) {
        Gate.ADMIT -> {
            true
        }

        Gate.PAIR_PAGE -> {
            reply.send(401, WebAssets.pairPage, "text/html; charset=UTF-8", "no-store")
            false
        }

        Gate.REFUSE -> {
            reply.send(401, PAIRING_REQUIRED, "text/plain; charset=UTF-8")
            false
        }
    }

/** `/pair?code=`: a right code sets the session cookie. */
internal fun RequestContext.handlePair(
    reply: Reply,
    params: Map<String, String>,
) {
    when (val result = pairing.tryPair(remoteAddress.orEmpty(), params["code"].orEmpty().trim())) {
        is PairResult.Paired -> reply.send(200, """{"ok":true}""", JSON, "no-store", SessionCookie.header(result.token))
        is PairResult.Locked -> reply.send(403, """{"ok":false,"retryAfter":${result.retryAfterSeconds}}""", JSON, "no-store")
        PairResult.Wrong -> reply.send(403, """{"ok":false}""", JSON, "no-store")
    }
}
