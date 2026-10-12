package io.github.aedev.flow.utils.cipher

import android.util.Log

/** Signature step as one multi-purpose function plus a pre-processing call: `H.sp, T=rb(6,3439,mi(24,1406,H.s))` (player 5203c085+). */
internal object SigPreprocessedCall {
    private const val TAG = "Flow_CipherFnExtract"

    // groups: fn, c1, c2, pre, p1, p2
    private val CALL =
        Regex(
            """\.sp\s*,\s*[a-zA-Z0-9${'$'}]+\s*=\s*([a-zA-Z0-9${'$'}]{2,})\(\s*(\d+)\s*,\s*(\d+)\s*,\s*([a-zA-Z0-9${'$'}]{2,})\(\s*(\d+)\s*,\s*(\d+)\s*,\s*[a-zA-Z0-9${'$'}]+\.s\s*\)\s*\)""",
        )

    fun find(playerJs: String): FunctionNameExtractor.SigFunctionInfo? {
        val (name, first, second, preprocess, preFirst, preSecond) = CALL.find(playerJs)?.destructured ?: return null
        Log.d(TAG, "SIG FUNCTION FOUND via the preprocessed call: $name($first, $second, $preprocess($preFirst, $preSecond, s))")
        return FunctionNameExtractor.SigFunctionInfo(
            name = name,
            constantArg = first.toInt(),
            constantArgs = listOf(first.toInt(), second.toInt()),
            preprocessFunc = preprocess,
            preprocessArgs = listOf(preFirst.toInt(), preSecond.toInt()),
            isHardcoded = false,
        )
    }
}
