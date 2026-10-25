package io.github.aedev.flow.localserver

import android.app.Application
import com.google.common.truth.Truth.assertThat
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class)
class TransportTest {
    @Test
    fun `large json is gzipped when asked`() =
        serverScenario {
            repeat(12) { client.get("/playlist_action?action=create&name=Playlist+number+$it+with+a+long+name") }
            val plain = client.get("/api/v1/playlists")
            val zipped = client.get("/api/v1/playlists") { header(HttpHeaders.AcceptEncoding, "gzip") }

            assertThat(plain.headers[HttpHeaders.ContentEncoding]).isNull()
            assertThat(plain.bodyAsText().length).isGreaterThan(512)
            assertThat(zipped.headers[HttpHeaders.ContentEncoding]).isEqualTo("gzip")
        }

    @Test
    fun `a small body is not compressed`() =
        serverScenario {
            val response = client.get("/api/v1/cast/stop") { header(HttpHeaders.AcceptEncoding, "gzip") }

            assertThat(response.headers[HttpHeaders.ContentEncoding]).isNull()
        }
}
