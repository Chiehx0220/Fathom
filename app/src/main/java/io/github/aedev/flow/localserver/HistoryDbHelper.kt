package io.github.aedev.flow.localserver

import android.content.Context

class HistoryDbHelper private constructor(
    val appContext: Context,
) {
    companion object {
        @Volatile
        private var instance: HistoryDbHelper? = null

        @JvmStatic
        @Synchronized
        fun getInstance(context: Context): HistoryDbHelper {
            var result = instance
            if (result == null) {
                result = HistoryDbHelper(context.applicationContext)
                instance = result
            }
            return result
        }
    }
}
