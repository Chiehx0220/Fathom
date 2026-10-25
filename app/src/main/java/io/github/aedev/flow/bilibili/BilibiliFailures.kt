package io.github.aedev.flow.bilibili

// Failure kinds; mapped by type.

/** Bilibili failure, apart from plain network error. */
sealed class BilibiliException(
    message: String,
) : Exception(message)

class BilibiliContentNotAvailableException(
    message: String,
) : BilibiliException(message)

class BilibiliGeoRestrictedException(
    message: String,
) : BilibiliException(message)

class BilibiliPaidContentException(
    message: String,
) : BilibiliException(message)

class BilibiliLiveNotStartedException(
    message: String,
) : BilibiliException(message)

/** Room replays uploads; unsupported by live player. */
class BilibiliLiveRebroadcastException(
    message: String,
) : BilibiliException(message)
