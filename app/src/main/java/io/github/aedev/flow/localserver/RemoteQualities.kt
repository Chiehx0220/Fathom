package io.github.aedev.flow.localserver

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray

/** JSON array of [id, label] pairs; malformed → empty. */
internal fun parseQualities(raw: String?): List<RemoteQuality> {
    if (raw.isNullOrEmpty()) return emptyList()
    return try {
        Json.parseToJsonElement(raw).jsonArray.mapNotNull { entry ->
            val pair = (entry as? JsonArray) ?: return@mapNotNull null
            val id = (pair.getOrNull(0) as? JsonPrimitive)?.content.orEmpty()
            val label = (pair.getOrNull(1) as? JsonPrimitive)?.content.orEmpty()
            if (id.isEmpty() || label.isEmpty()) null else RemoteQuality(id, label)
        }
    } catch (e: Exception) {
        emptyList()
    }
}
