package io.github.aedev.flow.data.account

import io.github.aedev.flow.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleAccountSession
    @Inject
    constructor(
        private val store: GoogleAccountStore,
    ) {
        fun start(scope: CoroutineScope) {
            scope.launch {
                store.account.collectLatest { account ->
                    YouTube.cookie = account?.cookie
                    YouTube.dataSyncId = account?.dataSyncId
                    YouTube.useLoginForBrowse = account != null
                }
            }
        }
    }
