package org.schabi.newpipe.localserver

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Foreground promotion + notification for the embedded local web server. Assumes NewPipe.init()
 * already ran via FlowApplication - re-initializing here would race the app's own extraction
 * pipeline (process-global state).
 */
class ServerService : Service() {

    private var server: LocalHttpServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            startServer()
        }
        return START_NOT_STICKY
    }

    private fun startServer() {
        HtmlRenderer.lightColors = DynamicColorHelper.getThemeColors(this, false)
        HtmlRenderer.darkColors = DynamicColorHelper.getThemeColors(this, true)

        ensureChannel()
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this, 0, it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(NOTIFICATION_TITLE)
            .setContentText("Listening on: $localAddress")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        try {
            val newServer = LocalHttpServer(this, PORT)
            server = newServer
            newServer.startServer()
            _running.value = true

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

            LocalHttpServer.log("Local server running at: $localAddress")
        } catch (e: Exception) {
            e.printStackTrace()
            LocalHttpServer.setLogListener(null)
            stopSelf()
        }
    }

    private fun stopServer() {
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wifiLock?.let { if (it.isHeld) runCatching { it.release() } }
        server?.stopServer()
        server = null
        _running.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // Android 15+ caps dataSync foreground services (~6h); the system then calls this and
    // expects stopSelf() within seconds, or throws ForegroundServiceDidNotStopInTimeException
    // (the crash this fixes). stopSelf() -> onDestroy() -> stopServer() releases the locks and
    // the notification. Restarting from here isn't allowed (background FGS start restriction),
    // so the server stays down until the user re-enables it in Settings.
    override fun onTimeout(startId: Int, fgsType: Int) {
        LocalHttpServer.log("Foreground service time limit reached, stopping local server")
        stopSelf(startId)
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

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
            return if (localIp != null) "http://$localIp:$PORT" else "http://127.0.0.1:$PORT"
        }

    companion object {
        private const val CHANNEL_ID = "flow_local_server"
        private const val NOTIFICATION_ID = 4097
        private const val NOTIFICATION_TITLE = "Local server running"
        const val PORT = 8080

        private val _running = MutableStateFlow(false)

        // True only while the socket is actually bound (set after startServer() succeeds, cleared
        // in stopServer()) - the Settings UI observes this instead of the saved "enabled"
        // preference, which stays true after the system stops the service (e.g. dataSync timeout)
        // or the process dies, so it used to keep showing "online" for a dead server.
        val runningState: StateFlow<Boolean> = _running.asStateFlow()

        val isRunning: Boolean
            get() = _running.value

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
        fun getLocalIpAddress(): String? {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val intf = interfaces.nextElement()
                    val addresses = intf.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val inetAddress: InetAddress = addresses.nextElement()
                        if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                            return inetAddress.hostAddress
                        }
                    }
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            return null
        }
    }
}
