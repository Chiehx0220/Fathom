package io.github.aedev.flow.bilibili.fake

import java.io.File

private const val UPDATE_ENV = "UPDATE_BASELINES"
private const val GOLDEN_DIR = "src/test/resources/bilibili-baselines"

/** Field-per-line toString; strings verbatim. */
internal fun dump(value: Any?): String {
    if (value is String) return value + "\n"
    val text = if (value is Throwable) "${value::class.simpleName}: ${value.message}" else value.toString()
    val out = StringBuilder()
    var depth = 0
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c == '(' || c == '[' -> {
                depth++
                out.append(c).append('\n').append("  ".repeat(depth))
            }

            c == ')' || c == ']' -> {
                depth = (depth - 1).coerceAtLeast(0)
                out.append('\n').append("  ".repeat(depth)).append(c)
            }

            c == ',' && text.getOrNull(i + 1) == ' ' -> {
                out.append(",\n").append("  ".repeat(depth))
                i++
            }

            else -> {
                out.append(c)
            }
        }
        i++
    }
    return out.append('\n').toString()
}

/** [actual] + [rawTail] vs baseline [name]; `UPDATE_BASELINES=1` rewrites. */
internal fun verifyGolden(
    name: String,
    actual: Any?,
    rawTail: String = "",
) {
    val text = dump(actual) + rawTail
    val file = File("$GOLDEN_DIR/$name.txt")
    if (System.getenv(UPDATE_ENV) != null) {
        file.parentFile?.mkdirs()
        file.writeText(text)
        return
    }
    check(file.exists()) { "No baseline $name; run with $UPDATE_ENV=1" }
    org.junit.Assert.assertEquals("baseline $name", file.readText(), text)
}
