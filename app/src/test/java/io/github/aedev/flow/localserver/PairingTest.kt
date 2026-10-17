package io.github.aedev.flow.localserver

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.security.SecureRandom

class PairingTest {
    private class MemoryStorage(
        var saved: PairingSnapshot? = null,
    ) : PairingStorage {
        override fun load() = saved

        override fun save(snapshot: PairingSnapshot) {
            saved = snapshot
        }
    }

    private var time = 1_000_000L
    private val storage = MemoryStorage()
    private val pairing = Pairing(storage, SecureRandom()) { time }

    private fun cookie(token: String) = "other=1; ${SessionCookie.NAME}=$token"

    private fun wrongCode() = if (pairing.code.value == "000000") "111111" else "000000"

    @Test
    fun `the code is six digits`() {
        assertThat(pairing.code.value).matches("[0-9]{6}")
    }

    @Test
    fun `the right code gives a cookie that is then recognised`() {
        val result = pairing.tryPair("10.0.0.2", pairing.code.value)

        assertThat(result).isInstanceOf(PairResult.Paired::class.java)
        assertThat(pairing.isPaired(cookie((result as PairResult.Paired).token))).isTrue()
    }

    @Test
    fun `a wrong code gives nothing`() {
        assertThat(pairing.tryPair("10.0.0.2", wrongCode())).isEqualTo(PairResult.Wrong)
        assertThat(pairing.isPaired(cookie("not-a-token"))).isFalse()
        assertThat(pairing.isPaired(null)).isFalse()
        assertThat(pairing.isPaired("${SessionCookie.NAME}=")).isFalse()
    }

    @Test
    fun `a peer is locked after a few misses, even with the right code`() {
        repeat(5) { pairing.tryPair("10.0.0.9", wrongCode()) }

        val locked = pairing.tryPair("10.0.0.9", pairing.code.value)
        assertThat(locked).isInstanceOf(PairResult.Locked::class.java)
        assertThat((locked as PairResult.Locked).retryAfterSeconds).isAtLeast(1)
        assertThat(pairing.tryPair("10.0.0.2", pairing.code.value)).isInstanceOf(PairResult.Paired::class.java)
    }

    @Test
    fun `a lock ends`() {
        repeat(5) { pairing.tryPair("10.0.0.9", wrongCode()) }
        time += 10 * 60_000L + 1

        assertThat(pairing.tryPair("10.0.0.9", pairing.code.value)).isInstanceOf(PairResult.Paired::class.java)
    }

    @Test
    fun `many misses from many peers retire the code`() {
        val before = pairing.code.value
        repeat(20) {
            pairing.tryPair(
                "10.0.1.$it",
                wrongCode().takeIf { code -> code != before } ?: "999999".takeIf { before != "999999" } ?: "888888",
            )
        }

        assertThat(pairing.code.value).isNotEqualTo(before)
    }

    @Test
    fun `a new code unpairs every browser`() {
        val token = (pairing.tryPair("10.0.0.2", pairing.code.value) as PairResult.Paired).token
        val before = pairing.code.value

        pairing.reset()

        assertThat(pairing.isPaired(cookie(token))).isFalse()
        assertThat(pairing.code.value).isNotEqualTo(before)
    }

    @Test
    fun `the code and paired browsers survive a restart`() {
        val token = (pairing.tryPair("10.0.0.2", pairing.code.value) as PairResult.Paired).token

        val restarted = Pairing(storage, SecureRandom()) { time }

        assertThat(restarted.code.value).isEqualTo(pairing.code.value)
        assertThat(restarted.isPaired(cookie(token))).isTrue()
    }

    @Test
    fun `the store keeps only hashes of the cookies`() {
        val token = (pairing.tryPair("10.0.0.2", pairing.code.value) as PairResult.Paired).token

        assertThat(storage.saved!!.sessions).doesNotContain(token)
        assertThat(storage.saved!!.sessions).hasSize(1)
    }

    @Test
    fun `only the newest browsers stay paired`() {
        val tokens = (1..21).map { (pairing.tryPair("10.0.0.$it", pairing.code.value) as PairResult.Paired).token }

        assertThat(pairing.isPaired(cookie(tokens.first()))).isFalse()
        assertThat(pairing.isPaired(cookie(tokens.last()))).isTrue()
    }
}
