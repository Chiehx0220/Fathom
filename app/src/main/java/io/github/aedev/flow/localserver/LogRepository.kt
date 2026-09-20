package org.schabi.newpipe.localserver

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogRepository private constructor() : LocalHttpServer.LogListener {

    interface LogListener {
        fun onLogAdded(formattedLine: String)
        fun onLogsCleared()
    }

    companion object {
        @Volatile
        private var instance: LogRepository? = null

        @JvmStatic
        @Synchronized
        fun getInstance(): LogRepository {
            var result = instance
            if (result == null) {
                result = LogRepository()
                instance = result
            }
            return result
        }
    }

    private val htmlLogLines = ArrayList<String>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var activeListener: LogListener? = null

    init {
        LocalHttpServer.setLogListener(this)
    }

    override fun onLog(message: String) {
        val time = dateFormat.format(Date())
        val formattedLine = formatLogToHtml(time, message)
        synchronized(htmlLogLines) {
            htmlLogLines.add(formattedLine)
            if (htmlLogLines.size > 500) {
                htmlLogLines.removeAt(0)
            }
        }
        synchronized(this) {
            activeListener?.onLogAdded(formattedLine)
        }
    }

    fun getLogs(): List<String> {
        synchronized(htmlLogLines) {
            return ArrayList(htmlLogLines)
        }
    }

    fun clear() {
        synchronized(htmlLogLines) {
            htmlLogLines.clear()
        }
        synchronized(this) {
            activeListener?.onLogsCleared()
        }
    }

    @Synchronized
    fun setActiveListener(listener: LogListener?) {
        this.activeListener = listener
    }

    private fun escapeHtml(text: String?): String {
        if (text == null) return ""
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
    }

    private fun formatLogToHtml(time: String, message: String): String {
        var displayMessage = message
        var isTruncated = false
        if (message.length > 200 || message.contains("\n")) {
            val newlineIdx = message.indexOf("\n")
            displayMessage = if (newlineIdx in 1 until 120) {
                message.substring(0, newlineIdx)
            } else {
                message.substring(0, minOf(message.length, 120))
            }
            isTruncated = true
        }

        var escapedMessage = escapeHtml(displayMessage)
        if (isTruncated) {
            escapedMessage += " <font color='#64748B'><b>[Truncated: ${message.length} chars]</b></font>"
        }

        val colorTime = "#64748B" // Slate-400
        var colorMessage = "#E2E8F0" // Slate-200 (default)

        val lowerMsg = displayMessage.lowercase(Locale.US)
        if (lowerMsg.contains("error") || lowerMsg.contains("exception") || lowerMsg.contains("failed")) {
            colorMessage = "#F87171" // Red-400
        } else if (lowerMsg.contains("started") || lowerMsg.contains("completed")) {
            colorMessage = "#4ADE80" // Green-400
        } else if (lowerMsg.contains("stopped")) {
            colorMessage = "#FB923C" // Orange-400
        } else if (lowerMsg.startsWith("request:")) {
            colorMessage = "#E2E8F0"
            if (escapedMessage.contains(" GET ")) {
                escapedMessage = escapedMessage.replace("Request:", "<font color='#F472B6'><b>REQ</b></font>") // Pink-400
                    .replace(" GET ", " <font color='#4ADE80'><b>GET</b></font> <font color='#38BDF8'>") // LightBlue-400
                escapedMessage += "</font>"
            } else if (escapedMessage.contains(" POST ")) {
                escapedMessage = escapedMessage.replace("Request:", "<font color='#F472B6'><b>REQ</b></font>")
                    .replace(" POST ", " <font color='#FB923C'><b>POST</b></font> <font color='#38BDF8'>")
                escapedMessage += "</font>"
            }
        } else if (lowerMsg.contains("proxying stream") || lowerMsg.contains("serving local")) {
            colorMessage = "#C084FC" // Purple-400
        }

        return if (lowerMsg.startsWith("request:")) {
            "<font color='$colorTime'>[$time]</font> $escapedMessage<br/>"
        } else {
            "<font color='$colorTime'>[$time]</font> <font color='$colorMessage'>$escapedMessage</font><br/>"
        }
    }
}
