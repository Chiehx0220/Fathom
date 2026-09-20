package io.github.aedev.flow.data.recommendation

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.recommendation.eval.NeuroEval.video
import org.junit.Test

class NeuroTokenizerCjkTest {
    private val tokenizer = NeuroTokenizer()

    @Test
    fun `a Chinese title becomes overlapping pairs that repeat across titles`() {
        val first = tokenizer.tokenize("海贼王一番赏")
        assertThat(first).containsExactly("海贼", "贼王", "王一", "一番", "番赏").inOrder()

        val second = tokenizer.tokenize("海贼王 新篇章")
        assertThat(first.intersect(second.toSet())).containsAtLeast("海贼", "贼王")
    }

    @Test
    fun `full-width punctuation separates words`() {
        assertThat(tokenizer.tokenize("伊娃科夫｜婷婷")).containsExactly("伊娃", "娃科", "科夫", "婷婷").inOrder()
    }

    @Test
    fun `function characters cut a run instead of pairing with their neighbours`() {
        assertThat(tokenizer.tokenize("杰哥的爸爸")).containsExactly("杰哥", "爸爸").inOrder()
        assertThat(tokenizer.tokenize("我们")).isEmpty()
    }

    @Test
    fun `Traditional Chinese is cut the same way as Simplified`() {
        assertThat(tokenizer.tokenize("海賊王一番賞")).containsExactly("海賊", "賊王", "王一", "一番", "番賞").inOrder()
        assertThat(tokenizer.tokenize("杰哥的爸爸")).containsExactly("杰哥", "爸爸").inOrder()
        assertThat(tokenizer.tokenize("這個我們")).isEmpty()
        assertThat(tokenizer.tokenize("沒有遊戲")).containsExactly("遊戲")
    }

    @Test
    fun `a two-character Chinese word is kept`() {
        assertThat(tokenizer.tokenize("路飞")).containsExactly("路飞")
    }

    @Test
    fun `a single Chinese character is dropped`() {
        assertThat(tokenizer.tokenize("我")).isEmpty()
    }

    @Test
    fun `Latin words in a mixed title are split off and keep the old length rule`() {
        assertThat(tokenizer.tokenize("iphone评测 ok")).containsExactly("iphone", "评测").inOrder()
    }

    @Test
    fun `English titles tokenize as before`() {
        assertThat(tokenizer.tokenize("Learning Kotlin coroutines!")).containsExactly("learn", "kotlin", "coroutines").inOrder()
    }

    @Test
    fun `a Chinese title makes topics of character pairs and no invented phrases`() {
        val features =
            tokenizer.extractFeatures(
                video("v1", title = "海贼王一番赏 人妖王", channelName = "杰哥"),
                IdfSnapshot(emptyMap(), totalDocs = 0),
            )

        assertThat(features.topics.keys).containsAtLeast("海贼", "贼王", "一番", "人妖", "妖王")
        // Every topic is a single word: no space means no phrase was made from neighbouring pairs.
        assertThat(features.topics.keys.filter { it.contains(' ') }).isEmpty()
    }

    @Test
    fun `two Chinese characters are a usable topic but two Latin letters are not`() {
        assertThat("海贼".isUsableTopic()).isTrue()
        assertThat("ab".isUsableTopic()).isFalse()
        assertThat("abc".isUsableTopic()).isTrue()
        assertThat(tokenizer.isNoiseTopic("海贼")).isFalse()
    }
}
