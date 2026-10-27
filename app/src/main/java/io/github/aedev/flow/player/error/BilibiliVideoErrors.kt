package io.github.aedev.flow.player.error

import android.content.Context
import io.github.aedev.flow.R
import io.github.aedev.flow.bilibili.BilibiliLiveException
import io.github.aedev.flow.bilibili.BilibiliLiveNotStartedException
import io.github.aedev.flow.bilibili.BilibiliLiveRebroadcastException

// VideoErrorMapper hook: live-room failures.
internal fun bilibiliLiveError(
    error: BilibiliLiveException,
    context: Context,
): VideoErrorMapper.VideoError =
    when (error) {
        is BilibiliLiveNotStartedException -> {
            VideoErrorMapper.VideoError(
                message = context.getString(R.string.error_live_not_started),
                hint = context.getString(R.string.error_live_not_started_hint),
                isRetryable = true,
                isUserActionable = false,
            )
        }

        is BilibiliLiveRebroadcastException -> {
            VideoErrorMapper.VideoError(
                message = context.getString(R.string.error_live_rebroadcast),
                hint = context.getString(R.string.error_live_rebroadcast_hint),
                isRetryable = false,
                isUserActionable = false,
            )
        }
    }
