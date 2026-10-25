package io.github.aedev.flow.localserver

import androidx.test.core.app.ApplicationProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.util.concurrent.Executors

private const val UPDATE_ENV = "UPDATE_BASELINES"
private val BASELINE_DIR = "src/test/resources/localserver-baselines"

private class MemoryPairingStorage : PairingStorage {
    override fun load(): PairingSnapshot? = null

    override fun save(snapshot: PairingSnapshot) = Unit
}

/** Request log of one scenario, compared to its baseline. */
internal class Recorder(
    private val client: HttpClient,
) {
    private val log = StringBuilder()
    private val masks = mutableListOf<Pair<String, String>>()

    suspend fun get(
        target: String,
        headers: Map<String, String> = emptyMap(),
    ) = call(HttpMethod.Get, target, headers)

    /** Generated [value] logged as [label]. */
    fun mask(
        value: String,
        label: String,
    ) {
        masks += value to label
    }

    suspend fun call(
        method: HttpMethod,
        target: String,
        headers: Map<String, String> = emptyMap(),
    ): String {
        val response =
            client.request(target) {
                this.method = method
                headers.forEach { (name, value) -> header(name, value) }
            }
        val type = response.contentType()?.withoutParameters()
        val body = response.bodyAsText()
        log.append("${method.value} $target")
        if (headers.isNotEmpty()) log.append("  $headers")
        log.append("\n=> ${response.status.value} $type\n")
        log.append(if (type?.match("application/json") == true) canonicalJson(body) else body).append("\n\n")
        return body
    }

    fun text() = masks.fold(log.toString()) { text, (value, label) -> text.replace(value, label) }
}

/** [installLocalServer] pipeline, Robolectric, loopback peer. */
internal fun serverScenario(block: suspend ApplicationTestBuilder.() -> Unit) =
    testApplication {
        val db = HistoryDbHelper.getInstance(ApplicationProvider.getApplicationContext())
        val pool = Executors.newCachedThreadPool()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        application { installLocalServer(db, pool, Pairing(MemoryPairingStorage()), RemoteSockets(scope)) }
        try {
            block()
        } finally {
            pool.shutdownNow()
        }
    }

internal suspend fun ApplicationTestBuilder.record(
    name: String,
    steps: suspend Recorder.() -> Unit,
) {
    val recorder = Recorder(client)
    recorder.steps()
    verifyBaseline(name, recorder.text())
}

internal fun verifyBaseline(
    name: String,
    actual: String,
) {
    val file = File("$BASELINE_DIR/$name.txt")
    if (System.getenv(UPDATE_ENV) != null) {
        file.parentFile?.mkdirs()
        file.writeText(actual)
        return
    }
    check(file.exists()) { "No baseline $name; run with $UPDATE_ENV=1" }
    org.junit.Assert.assertEquals("baseline $name", file.readText(), actual)
}

/** Parsed form: sorted keys, 2-space indent; order/whitespace-insensitive. */
internal fun canonicalJson(text: String): String =
    try {
        StringBuilder().also { write(it, JSONTokener(text).nextValue(), 0) }.toString()
    } catch (e: Exception) {
        text
    }

private fun write(
    out: StringBuilder,
    value: Any?,
    depth: Int,
) {
    val pad = "  ".repeat(depth + 1)
    when (value) {
        is JSONObject -> {
            val keys =
                value
                    .keys()
                    .asSequence()
                    .toList()
                    .sorted()
            if (keys.isEmpty()) return run { out.append("{}") }
            out.append("{\n")
            keys.forEachIndexed { i, key ->
                out.append(pad).append(JSONObject.quote(key)).append(": ")
                write(out, value.get(key), depth + 1)
                out.append(if (i < keys.lastIndex) ",\n" else "\n")
            }
            out.append("  ".repeat(depth)).append("}")
        }

        is JSONArray -> {
            if (value.length() == 0) return run { out.append("[]") }
            out.append("[\n")
            for (i in 0 until value.length()) {
                out.append(pad)
                write(out, value.get(i), depth + 1)
                out.append(if (i < value.length() - 1) ",\n" else "\n")
            }
            out.append("  ".repeat(depth)).append("]")
        }

        is String -> {
            out.append(JSONObject.quote(value))
        }

        JSONObject.NULL, null -> {
            out.append("null")
        }

        // 3 == 3.0 in JS
        is Number -> {
            out.append(
                java.math
                    .BigDecimal(value.toString())
                    .stripTrailingZeros()
                    .toPlainString(),
            )
        }

        else -> {
            out.append(value.toString())
        }
    }
}
