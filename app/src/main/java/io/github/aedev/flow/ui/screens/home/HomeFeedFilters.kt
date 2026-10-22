package io.github.aedev.flow.ui.screens.home

import io.github.aedev.flow.data.model.Video

/** Drops watched ids. */
internal fun List<Video>.filterWatched(watchedIds: Set<String>): List<Video> =
    if (watchedIds.isEmpty()) this else filter { it.id !in watchedIds }

/** Long-form with a known duration, or live; excludes Shorts. */
internal fun List<Video>.filterValid(): List<Video> = filter { !it.isShort && (it.duration > 0 || it.isLive) }
