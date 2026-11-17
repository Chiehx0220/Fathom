package io.github.aedev.flow.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.emptyFlow

/** A [LiveChatRepository] whose Bilibili feed never produces a message. */
internal fun liveChatRepository() = LiveChatRepository(BilibiliLiveChatFeed({ emptyFlow() }, CoroutineScope(Job())))
