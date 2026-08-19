package com.example.annarboard.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.annarboard.R
import com.example.annarboard.network.Constants
import com.example.annarboard.network.RetrofitClient
import com.example.annarboard.theme.SettingsManager
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.*

data class BusTrackingData(
    val title: String,
    val primaryRoute: String,
    val primaryEtaText: String,
    val arrivalTimeText: String,
    val upcomingText: String,
    val progress: Int
)

class TrackingService : Service() {

    companion object {
        const val ACTION_START_TRACKING = "com.example.annarboard.START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.example.annarboard.STOP_TRACKING"
        const val ACTION_REVERSE_DIRECTION = "com.example.annarboard.REVERSE_DIRECTION"
        
        const val EXTRA_ORIGIN_HUB = "ORIGIN_HUB"
        const val EXTRA_DESTINATION_HUB = "DESTINATION_HUB"
        const val EXTRA_BUS_ID = "BUS_ID"
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private var originKey = "cctc"
    private var destinationKey = "pierpont"
    private var targetBusId: String? = null
    
    private var pollingJob: Job? = null
    private var isArrivalCountdownStarted = false

    private val CHANNEL_ID = "BusTracker_Public_v5"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_STOP_TRACKING) {
            stopTrackingAndSelf()
            return START_NOT_STICKY
        }

        if (action == ACTION_REVERSE_DIRECTION) {
            val temp = originKey
            originKey = destinationKey
            destinationKey = temp
            isArrivalCountdownStarted = false
            triggerPoll()
            return START_STICKY
        }

        intent?.getStringExtra(EXTRA_ORIGIN_HUB)?.let { originKey = it }
        intent?.getStringExtra(EXTRA_DESTINATION_HUB)?.let { destinationKey = it }
        intent?.getStringExtra(EXTRA_BUS_ID)?.let { targetBusId = it }

        val initialData = BusTrackingData(
            title = "Live Bus Tracker",
            primaryRoute = "MBus",
            primaryEtaText = "Loading...",
            arrivalTimeText = "Fetching predictions...",
            upcomingText = "Please wait...",
            progress = 0
        )

        val notification = buildNotification(initialData)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }

        startPollingLoop()

        return START_STICKY
    }

    private fun startPollingLoop() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                pollData()
                
                val settingsManager = SettingsManager(this@TrackingService)
                val intervalSec = settingsManager.currentSettings.value.updateFrequencyMBus.coerceAtLeast(5)
                delay(intervalSec * 1000L)
            }
        }
    }

    private fun triggerPoll() {
        scope.launch { pollData() }
    }

    private suspend fun pollData() {
        try {
            val originHub = Constants.HUBS.find { it.key == originKey } ?: Constants.HUBS[0]
            val destHub = Constants.HUBS.find { it.key == destinationKey } ?: Constants.HUBS[1]

            val response = RetrofitClient.instance.getMBusPredictions(originHub.stopIds.joinToString(","))
            val prdList = response.bustimeResponse?.prd ?: emptyList()

            val originKeyLower = originHub.key.lowercase()
            val destKeyLower = destHub.key.lowercase()
            val fromCentral = originKeyLower in listOf("cctc", "union")
            val toNorth = destKeyLower in listOf("pierpont", "fxb", "bursley", "art")
            val fromNorth = originKeyLower in listOf("pierpont", "fxb", "bursley", "art")
            val toCentral = destKeyLower in listOf("cctc", "union")

            var allowedRoutes = emptyList<String>()
            if (fromCentral && toNorth) allowedRoutes = Constants.ROUTE_MAP["central-to-north"] ?: emptyList()
            else if (fromNorth && toCentral) allowedRoutes = Constants.ROUTE_MAP["north-to-central"] ?: emptyList()

            var filtered = if (allowedRoutes.isNotEmpty()) {
                prdList.filter { allowedRoutes.contains(it.rt) }
            } else {
                prdList.filter { p ->
                    val des = p.des.lowercase()
                    destHub.keywords.any { kw -> des.contains(kw.lowercase()) }
                }
            }

            if (!targetBusId.isNullOrEmpty()) {
                val targeted = filtered.filter { it.rt.equals(targetBusId, ignoreCase = true) }
                if (targeted.isNotEmpty()) filtered = targeted
            }

            val sortedArrivals = filtered.map { p ->
                val mins = if (p.prdctdn.equals("DUE", ignoreCase = true)) 0 else p.prdctdn.toIntOrNull() ?: 99
                p to mins
            }.sortedBy { it.second }

            val title = "${cleanHubName(originHub.name)} → ${cleanHubName(destHub.name)}"

            if (sortedArrivals.isEmpty()) {
                val emptyData = BusTrackingData(
                    title = title,
                    primaryRoute = "MBus",
                    primaryEtaText = "No buses",
                    arrivalTimeText = "No scheduled buses found",
                    upcomingText = "Check back later",
                    progress = 0
                )
                updateNotification(emptyData)
                return
            }

            val topArrivals = sortedArrivals.take(3)
            val nextBus = topArrivals.first()
            val nextBusMins = nextBus.second
            val primaryRoute = nextBus.first.rt

            val primaryEtaText = if (nextBusMins == 0) "NOW" else "$nextBusMins min"
            val tripDur = estimateTripDuration(primaryRoute)
            val arrivalTimeMillis = System.currentTimeMillis() + nextBusMins * 60 * 1000L
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            val arrivalTimeText = "Arrives ~${sdf.format(Date(arrivalTimeMillis))} · $tripDur min ride"

            val upcomingList = topArrivals.drop(1)
            val upcomingText = if (upcomingList.isNotEmpty()) {
                "Also coming: " + upcomingList.joinToString(", ") { (_, m) -> if (m == 0) "NOW" else "$m min" }
            } else {
                "No other upcoming buses"
            }

            val progress = ((15 - nextBusMins).coerceIn(0, 15).toFloat() / 15f * 100).toInt()

            val trackingData = BusTrackingData(
                title = title,
                primaryRoute = primaryRoute,
                primaryEtaText = primaryEtaText,
                arrivalTimeText = arrivalTimeText,
                upcomingText = upcomingText,
                progress = progress
            )

            updateNotification(trackingData)

            // Auto-dismiss logic: When nearest bus arrives (mins == 0), notify & stop service after 60s
            if (nextBusMins == 0 && !isArrivalCountdownStarted) {
                isArrivalCountdownStarted = true
                scope.launch {
                    val arrivalData = trackingData.copy(
                        primaryEtaText = "ARRIVED",
                        arrivalTimeText = "Bus has arrived at ${cleanHubName(originHub.name)}!",
                        upcomingText = "Tracker auto-closing in 60s...",
                        progress = 100
                    )
                    updateNotification(arrivalData)
                    delay(60000L)
                    stopTrackingAndSelf()
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            val errorData = BusTrackingData(
                title = "Bus Live Tracker",
                primaryRoute = "MBus",
                primaryEtaText = "Error",
                arrivalTimeText = "Retrying shortly...",
                upcomingText = "Connection issue",
                progress = 0
            )
            updateNotification(errorData)
        }
    }

    private fun estimateTripDuration(route: String): Int {
        return when (route.uppercase()) {
            "CN", "CS" -> 18
            "NW" -> 15
            "BB" -> 20
            "WX" -> 12
            else -> 15
        }
    }

    private fun cleanHubName(name: String): String {
        return name
            .replace("Central Campus (CCTC)", "CCTC", ignoreCase = true)
            .replace("Central Campus", "CCTC", ignoreCase = true)
            .replace("Pierpont Commons", "Pierpont", ignoreCase = true)
    }

    private fun stopTrackingAndSelf() {
        job.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun updateNotification(data: BusTrackingData) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(1, buildNotification(data))
    }

    private fun buildNotification(data: BusTrackingData): Notification {
        Log.d("BusNotif", "Inflating RemoteViews for ${data.title}...")

        val reverseIntent = Intent(this, TrackingService::class.java).apply {
            action = ACTION_REVERSE_DIRECTION
        }
        val pendingReverse = PendingIntent.getService(
            this, 0, reverseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, TrackingService::class.java).apply {
            action = ACTION_STOP_TRACKING
        }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Collapsed RemoteViews
        val compactViews = RemoteViews(packageName, R.layout.notification_bus_tracker_compact).apply {
            setTextViewText(R.id.tv_route_title, data.title)
            setTextViewText(R.id.tv_eta_primary, data.primaryEtaText)
            setTextViewText(R.id.tv_arrival_time, data.arrivalTimeText)
            setProgressBar(R.id.notification_progress, 100, data.progress, false)
        }

        // Expanded Card RemoteViews
        val expandedViews = RemoteViews(packageName, R.layout.notification_bus_tracker).apply {
            setTextViewText(R.id.tv_route_title, data.title)
            setTextViewText(R.id.tv_route_badge, data.primaryRoute)
            setTextViewText(R.id.tv_label_next, "NEXT BUS")
            setTextViewText(R.id.tv_eta_primary, data.primaryEtaText)
            setTextViewText(R.id.tv_arrival_time, data.arrivalTimeText)
            setProgressBar(R.id.notification_progress, 100, data.progress, false)
            setTextViewText(R.id.tv_upcoming, data.upcomingText)
        }

        Log.d("BusNotif", "RemoteViews inflated OK")

        // Public version displayed when lockscreen content is set to private
        val publicNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(data.title)
            .setContentText("${data.primaryRoute}: ${data.primaryEtaText} (${data.arrivalTimeText})")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val extras = Bundle().apply {
            putBoolean("android.requestPromotedOngoing", true)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(compactViews)
            .setCustomBigContentView(expandedViews)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPublicVersion(publicNotification)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .addExtras(extras)
            .addAction(android.R.drawable.ic_menu_revert, "Reverse Route", pendingReverse)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Tracking", pendingStop)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            try {
                manager?.deleteNotificationChannel("TrackingChannel")
                manager?.deleteNotificationChannel("TrackingChannel_v2")
                manager?.deleteNotificationChannel("TrackingChannel_v3")
                manager?.deleteNotificationChannel("BusTracker_RichCard_v4")
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bus Live Tracker",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Live persistent bus tracker notification card"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
