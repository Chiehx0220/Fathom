package io.github.aedev.flow.localserver

import org.json.JSONArray

/** Parses a JSON array of [id, label] pairs; malformed input yields an empty list. */
internal fun parseQualities(raw: String?): List<RemoteQuality> {
    if (raw.isNullOrEmpty()) return emptyList()
    return try {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val pair = array.optJSONArray(index) ?: return@mapNotNull null
            val id = pair.optString(0, "")
            val label = pair.optString(1, "")
            if (id.isEmpty() || label.isEmpty()) null else RemoteQuality(id, label)
        }
    } catch (e: Exception) {
        emptyList()
    }
}
