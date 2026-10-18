/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChineseFunctionWordsTest {
    private val tokenizer = NeuroTokenizer()

    @Test
    fun `function words are not topic sized in either script`() {
        listOf("我们", "我們", "这个", "這個", "什么", "什麼", "为什么", "為什麼", "没有", "沒有", "但是").forEach {
            assertThat(NeuroText.isTopicSized(it)).isFalse()
        }
    }

    @Test
    fun `ordinary Chinese words stay topic sized`() {
        listOf("海贼", "海賊", "游戏", "遊戲", "评测", "評測", "教学", "料理").forEach {
            assertThat(NeuroText.isTopicSized(it)).isTrue()
        }
    }

    @Test
    fun `title boilerplate and audience level are not topic sized in either script`() {
        listOf("分鐘", "分钟", "推薦", "推荐", "懶人包", "懒人包", "官方", "獨家", "独家", "系列", "每週", "新手", "入門", "入门", "零基礎", "零基础").forEach {
            assertThat(NeuroText.isTopicSized(it)).isFalse()
        }
    }

    @Test
    fun `subjects that sit next to boilerplate stay topic sized`() {
        listOf("電影", "电影", "遊戲", "游戏", "美食", "旅遊", "吉他", "演唱會", "股票", "系統", "筆記", "更新").forEach {
            assertThat(NeuroText.isTopicSized(it)).isTrue()
        }
    }

    @Test
    fun `a title of only function words yields no topics`() {
        assertThat(tokenizer.tokenize("我们")).isEmpty()
        assertThat(tokenizer.tokenize("這個")).isEmpty()
    }

    @Test
    fun `a function word does not displace the topic next to it`() {
        val topics = tokenizer.tokenize("这个 kotlin")
        assertThat(topics).contains("kotlin")
        assertThat(topics).doesNotContain("这个")
    }
}
