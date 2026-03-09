package com.mfaizanse.batteryinfoserver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.net.NetworkInterface

class BatteryInfoService : Service() {

    companion object {
        private const val TAG = "BatteryInfoService"
        private const val NOTIFICATION_CHANNEL_ID = "battery_server_channel"
        private const val NOTIFICATION_ID = 1001
        private const val SERVER_PORT = 9091

        const val ACTION_START = "com.mfaizanse.batteryinfoserver.ACTION_START"
        const val ACTION_STOP = "com.mfaizanse.batteryinfoserver.ACTION_STOP"
    }

    private var httpServer: BatteryHttpServer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // -------------------------------------------------------------------------
    // Service lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForegroundService()
            return START_NOT_STICKY
        }

        // Default: start (or re-start) the server
        startForeground(NOTIFICATION_ID, buildNotification())
        startHttpServer()

        // START_STICKY: if the OS kills this service under memory pressure,
        // it will be restarted automatically with a null intent.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopHttpServer()
        releaseWakeLock()
        super.onDestroy()
    }

    // -------------------------------------------------------------------------
    // HTTP server management
    // -------------------------------------------------------------------------

    private fun startHttpServer() {
        if (httpServer == null) {
            httpServer = BatteryHttpServer(SERVER_PORT)
        }
        if (httpServer?.wasStarted() == false) {
            try {
                httpServer!!.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                Log.i(TAG, "HTTP server started on port $SERVER_PORT")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start HTTP server", e)
            }
        }
    }

    private fun stopHttpServer() {
        httpServer?.stop()
        httpServer = null
        Log.i(TAG, "HTTP server stopped")
    }

    // -------------------------------------------------------------------------
    // Foreground service helpers
    // -------------------------------------------------------------------------

    private fun stopForegroundService() {
        stopHttpServer()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Battery Info Server",
            NotificationManager.IMPORTANCE_LOW  // no sound, still shows in status bar
        ).apply {
            description = "Keeps the HTTP server running in the background"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Battery Info Server")
            .setContentText("Running on port $SERVER_PORT")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    // -------------------------------------------------------------------------
    // WakeLock management
    // -------------------------------------------------------------------------

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$TAG::WakeLock"
        ).apply { acquire() }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    // -------------------------------------------------------------------------
    // NanoHTTPD inner class
    // -------------------------------------------------------------------------

    inner class BatteryHttpServer(port: Int) : NanoHTTPD(port) {

        override fun serve(session: IHTTPSession): Response {
            return when (session.uri) {
                "/battery" -> serveBattery()
                else -> newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    "application/json",
                    """{"error":"Not Found","available_endpoints":["/battery"]}"""
                )
            }
        }

        private fun serveBattery(): Response {
            return try {
                val json = buildBatteryJson()
                newFixedLengthResponse(Response.Status.OK, "application/json", json)
            } catch (e: Exception) {
                Log.e(TAG, "Error building battery JSON", e)
                newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    "application/json",
                    """{"error":"${e.message}"}"""
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Data gathering
    // -------------------------------------------------------------------------

    private fun buildBatteryJson(): String {
        val root = JSONObject().apply {
            put("status", readBatteryStatus())
            put("device", readDeviceInfo())
            put("environment", readEnvironmentInfo())
        }
        return root.toString(2)
    }

    private fun readBatteryStatus(): JSONObject {
        // Registering with null receiver reads the last sticky battery broadcast immediately.
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val levelPct = if (scale > 0) (level * 100 / scale) else -1

        // EXTRA_TEMPERATURE is in tenths of a degree Celsius (e.g. 295 = 29.5°C)
        val rawTemp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC = rawTemp / 10.0

        val voltage = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0

        val health = when (batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH,
                BatteryManager.BATTERY_HEALTH_UNKNOWN)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "cold"
            else -> "unknown"
        }

        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val powerSource = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "ac"
            BatteryManager.BATTERY_PLUGGED_USB -> "usb"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            BatteryManager.BATTERY_PLUGGED_DOCK -> "dock"
            0 -> "unplugged"
            else -> "unknown"
        }

        return JSONObject().apply {
            put("level", levelPct)
            put("temp", "$tempC°C")
            put("voltage", voltage)
            put("health", health)
            put("is_charging", isCharging)
            put("power_source", powerSource)
        }
    }

    private fun readDeviceInfo(): JSONObject {
        val uptimeSeconds = SystemClock.elapsedRealtime() / 1000L

        return JSONObject().apply {
            put("model", Build.MODEL)
            put("manufacturer", Build.MANUFACTURER)
            put("uptime_seconds", uptimeSeconds)
            put("ip_address", getDeviceIpAddress())
        }
    }

    private fun getDeviceIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "unknown"
            for (iface in interfaces.asSequence()) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses.asSequence()) {
                    if (addr.isLoopbackAddress) continue
                    val hostAddr = addr.hostAddress ?: continue
                    // Skip IPv6 addresses (they contain ':')
                    if (!hostAddr.contains(':')) return hostAddr
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not determine IP address", e)
        }
        return "unknown"
    }

    private fun readEnvironmentInfo(): JSONObject {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        @Suppress("DEPRECATION")
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        return JSONObject().apply {
            put("wifi_signal", getWifiSignalStrength(wm))
            put("data_connection", getDataConnectionType(cm))
        }
    }

    @Suppress("DEPRECATION")
    private fun getWifiSignalStrength(wm: WifiManager): Int {
        val wifiInfo = wm.connectionInfo
        return if (wifiInfo != null && wifiInfo.networkId != -1) {
            wifiInfo.rssi
        } else {
            -1
        }
    }

    private fun getDataConnectionType(cm: ConnectivityManager): String {
        val network = cm.activeNetwork ?: return "none"
        val caps = cm.getNetworkCapabilities(network) ?: return "none"

        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "bluetooth"
            else -> "other"
        }
    }
}
