package io.github.aedev.flow.localserver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import io.github.aedev.flow.localserver.remote.SETTINGS_ROUTE
import io.github.aedev.flow.widget.core.action.WidgetDeepLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Foreground promotion + notification for embedded web server; assumes NewPipe.init() ran (re-init races extraction). */
class ServerService : Service() {
    private var server: LocalHttpServer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var handoffJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (!isRunning) {
            startServer()
        }
        return START_NOT_STICKY
    }

    private fun startServer() {
        WebTheme.lightColors = DynamicColorHelper.getThemeColors(this, false)
        WebTheme.darkColors = DynamicColorHelper.getThemeColors(this, true)

        ensureChannel()
        mutablePortsBusy.value = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification(null), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification(null))
        }

        try {
            val bound =
                startOnFreePort {
                    LocalHttpServer(this, it).also { candidate ->
                        runCatching { candidate.startServer() }.onFailure {
                            candidate.stopServer()
                            throw it
                        }
                    }
                }
            if (bound == null) {
                mutablePortsBusy.value = true
                serverLog("Ports $FIRST_PORT-${FIRST_PORT + PORT_SPAN - 1} are all in use")
                stopSelf()
                return
            }
            server = bound.value
            mutablePort.value = bound.port
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(localAddress))
            mutableRunning.value = true
            val delivery = HandoffDelivery(this)
            handoffJob = serviceScope.launch { RemoteSession.handoffs.collect { runCatching { delivery.deliver(it) } } }

            try {
                val pm = getSystemService(POWER_SERVICE) as PowerManager?
                if (pm != null && wakeLock?.isHeld != true) {
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FlowLocalServer::WakeLock").apply { acquire() }
                }
                val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager?
                if (wm != null && wifiLock?.isHeld != true) {
                    wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "FlowLocalServer::WifiLock").apply { acquire() }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            serverLog("Local server running at: $localAddress")
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun stopServer() {
        handoffJob?.cancel()
        handoffJob = null
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wifiLock?.let { if (it.isHeld) runCatching { it.release() } }
        server?.stopServer()
        server = null
        mutableRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // Android 15+ caps dataSync FGS (~6h): stopSelf() → stopServer(), no restart (background start limit).
    override fun onTimeout(
        startId: Int,
        fgsType: Int,
    ) {
        serverLog("Foreground service time limit reached, stopping local server")
        stopSelf(startId)
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    // Settings lists the server switch first.
    private fun notification(address: String?) =
        NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle(NOTIFICATION_TITLE)
            .apply { if (address != null) setContentText("Listening on: $address") }
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    WidgetDeepLink.openRoute(this, SETTINGS_ROUTE),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            ).setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(CHANNEL_ID, "Local web server", NotificationManager.IMPORTANCE_LOW)
                channel.setSound(null, null)
                channel.enableVibration(false)
                channel.setShowBadge(false)
                manager.createNotificationChannel(channel)
            }
        }
    }

    val localAddress: String
        get() {
            val localIp = getLocalIpAddress()
            return if (localIp != null) "http://$localIp:$port" else "http://127.0.0.1:$port"
        }

    companion object {
        private const val CHANNEL_ID = "flow_local_server"
        private const val NOTIFICATION_ID = 4097
        private const val NOTIFICATION_TITLE = "Local server running"
        private val mutablePort = MutableStateFlow(FIRST_PORT)

        /** Port the running server bound (the first free one from [FIRST_PORT]). */
        val port: Int
            get() = mutablePort.value

        private val mutablePortsBusy = MutableStateFlow(false)

        /** True after a start found every port taken. */
        val portsBusy: StateFlow<Boolean> = mutablePortsBusy.asStateFlow()

        private val mutableRunning = MutableStateFlow(false)

        // Socket-bound state; Settings observes it, not saved "enabled" (stale after system stop).
        val runningState: StateFlow<Boolean> = mutableRunning.asStateFlow()

        val isRunning: Boolean
            get() = mutableRunning.value

        fun start(context: Context) {
            val intent = Intent(context, ServerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ServerService::class.java))
        }

        @JvmStatic
        fun getLocalIpAddress(): String? = lanAddress()
    }
}
