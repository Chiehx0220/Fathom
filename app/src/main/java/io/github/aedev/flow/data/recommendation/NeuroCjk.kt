/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 *
 * Flow is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 */

package io.github.aedev.flow.data.recommendation

/*
 * Chinese and Japanese in the recommender's text handling.
 *
 * Both are written without spaces between words, so the whitespace splitting the tokenizer does for
 * other languages would make a whole sentence one word. Everything that is about those scripts lives
 * here, and the tokenizer only calls in: [splitWords] for the words of a text, and [isUsableTopic]
 * wherever a rule says how short a topic may be.
 */

/** Han ideographs and kana: scripts written without spaces between words. */
internal fun isCjk(ch: Char): Boolean =
    when (Character.UnicodeScript.of(ch.code)) {
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        -> true
        else -> false
    }

/**
 * Particles, pronouns and other function characters of Chinese, in Simplified and Traditional forms.
 * They say nothing about a topic, and left in they pair up with their neighbours ("男的", "我们")
 * into pairs that mean nothing, so a run of Han characters is cut at them, as it is at punctuation.
 */
private val CJK_FUNCTION_CHARS =
    (
        // Simplified, then the characters whose Traditional form differs.
        "的了着过吗呢吧啊呀嘛哦和与及而也就都还又很把被是在有不没我你他她它们这那个么之" +
            "著過嗎與還沒們這個麼"
    ).toSet()

/**
 * Long enough to be a topic. Three characters for alphabetic scripts; two are enough in Chinese
 * or Japanese, where a two-character word is an ordinary word.
 */
internal fun String.isUsableTopic(): Boolean = length >= 3 || (length >= 2 && any(::isCjk))

/**
 * Words of [text], lowercased. A run of Han/kana characters is cut into overlapping pairs
 * ("海贼王一番赏" -> 海贼, 贼王, 王一, 一番, 番赏): a dictionary-free way to get units that repeat from
 * one title to the next. Anything else is split on spaces, and full-width punctuation or a Chinese
 * function character inside such a run separates words. Other words shorter than [minLength] are
 * dropped.
 */
internal fun splitWords(
    text: String,
    minLength: Int,
): List<String> {
    val words = ArrayList<String>()
    for (raw in text.lowercase().split(NeuroTokenizer.WHITESPACE_REGEX)) {
        val word = raw.trim { !it.isLetterOrDigit() }
        if (word.isEmpty()) continue
        if (!word.any(::isCjk)) {
            if (word.length >= minLength) words += word
            continue
        }
        val run = StringBuilder()
        var runIsCjk = false

        fun flush() {
            if (run.isEmpty()) return
            if (runIsCjk) {
                for (i in 0 until run.length - 1) words += run.substring(i, i + 2)
            } else if (run.length >= minLength) {
                words += run.toString()
            }
            run.setLength(0)
        }
        for (ch in word) {
            if (!ch.isLetterOrDigit() || ch in CJK_FUNCTION_CHARS) {
                flush()
                continue
            }
            val cjk = isCjk(ch)
            if (run.isNotEmpty() && cjk != runIsCjk) flush()
            runIsCjk = cjk
            run.append(ch)
        }
        flush()
    }
    return words
}
