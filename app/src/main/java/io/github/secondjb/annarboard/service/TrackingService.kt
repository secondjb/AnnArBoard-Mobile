package io.github.secondjb.annarboard.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import io.github.secondjb.annarboard.R
import io.github.secondjb.annarboard.network.Constants
import io.github.secondjb.annarboard.network.RetrofitClient
import io.github.secondjb.annarboard.theme.SettingsManager
import android.util.Log
import android.util.TypedValue
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
    val progress: Int,
    val arrivalTimeMillis: Long = 0L,
    val nextBusMins: Int = 0
)

class TrackingService : Service() {

    companion object {
        const val ACTION_START_TRACKING = "io.github.secondjb.annarboard.START_TRACKING"
        const val ACTION_STOP_TRACKING = "io.github.secondjb.annarboard.STOP_TRACKING"
        const val ACTION_REVERSE_DIRECTION = "io.github.secondjb.annarboard.REVERSE_DIRECTION"
        const val ACTION_CYCLE_BUS_ROUTE = "io.github.secondjb.annarboard.CYCLE_BUS_ROUTE"
        
        const val EXTRA_ORIGIN_HUB = "ORIGIN_HUB"
        const val EXTRA_DESTINATION_HUB = "DESTINATION_HUB"
        const val EXTRA_BUS_ID = "BUS_ID"

        @Volatile
        var isServiceRunning = false
            private set
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private var originKey = "cctc"
    private var destinationKey = "pierpont"
    private var targetBusId: String? = null
    private var availableRoutesList: List<String> = emptyList()
    
    private var pollingJob: Job? = null
    private var isArrivalCountdownStarted = false

    private val CHANNEL_ID = "bus_tracker_live"

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isServiceRunning = true
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

        if (action == ACTION_CYCLE_BUS_ROUTE) {
            if (availableRoutesList.isNotEmpty()) {
                val options = listOf(null) + availableRoutesList
                val currentIdx = options.indexOf(targetBusId)
                val nextIdx = (currentIdx + 1) % options.size
                targetBusId = options[nextIdx]
                Log.d("TrackingService", "Cycled route to: $targetBusId")
            }
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

            availableRoutesList = filtered.map { it.rt }.distinct()

            if (!targetBusId.isNullOrEmpty()) {
                val targeted = filtered.filter { it.rt.equals(targetBusId, ignoreCase = true) }
                if (targeted.isNotEmpty()) filtered = targeted
            }

            val sortedArrivals = filtered.map { p ->
                val mins = if (p.prdctdn.equals("DUE", ignoreCase = true)) 0 else p.prdctdn.toIntOrNull() ?: 99
                p to mins
            }.sortedBy { it.second }

            val baseTitle = "${cleanHubName(originHub.name)} → ${cleanHubName(destHub.name)}"

            if (sortedArrivals.isEmpty()) {
                val emptyData = BusTrackingData(
                    title = "$baseTitle · MBus · No buses",
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

            val etaDisplay = if (nextBusMins == 0) "Now" else "${nextBusMins}m"
            val title = "$baseTitle · $primaryRoute · $etaDisplay"

            val primaryEtaText = if (nextBusMins == 0) "NOW" else "$nextBusMins min"
            val tripDur = estimateTripDuration(primaryRoute)
            val stopsCount = estimateStopsCount(primaryRoute)
            val arrivalTimeMillis = System.currentTimeMillis() + nextBusMins * 60 * 1000L
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            
            val routePrefix = if (!targetBusId.isNullOrEmpty()) "Tracking $targetBusId · " else ""
            val arrivalTimeText = if (nextBusMins <= 0) {
                "Bus ($primaryRoute) has arrived at ${cleanHubName(originHub.name)}! Ride $stopsCount stops to ${cleanHubName(destHub.name)}."
            } else {
                "${routePrefix}Arrives ${sdf.format(Date(arrivalTimeMillis))} · $tripDur min ride ($stopsCount stops)"
            }

            val upcomingList = topArrivals.drop(1)
            val upcomingText = if (upcomingList.isNotEmpty()) {
                "Also: " + upcomingList.joinToString(", ") { (p, m) ->
                    val mStr = if (m == 0) "NOW" else "$m min"
                    "${p.rt} ($mStr)"
                }
            } else {
                "No other upcoming buses"
            }

            // Calculate progress percentage based on total trip duration (0% = start of trip, 100% = NOW)
            val totalMinutes = tripDur.coerceAtLeast(1)
            val remainingMinutes = nextBusMins
            val progressPercent = ((totalMinutes - remainingMinutes).toFloat() / totalMinutes * 100).toInt().coerceIn(0, 100)

            Log.d("BusNotif", "Updating progress: $progressPercent% | Next bus: ${nextBusMins}min")

            val trackingData = BusTrackingData(
                title = title,
                primaryRoute = primaryRoute,
                primaryEtaText = primaryEtaText,
                arrivalTimeText = arrivalTimeText,
                upcomingText = upcomingText,
                progress = progressPercent,
                arrivalTimeMillis = arrivalTimeMillis,
                nextBusMins = nextBusMins
            )

            updateNotification(trackingData)

            // Auto-dismiss logic: When nearest bus arrives (mins == 0), notify & stop service after 60s
            if (nextBusMins == 0 && !isArrivalCountdownStarted) {
                isArrivalCountdownStarted = true
                scope.launch {
                    val arrivalData = trackingData.copy(
                        primaryEtaText = "ARRIVED",
                        arrivalTimeText = "Bus has arrived at ${cleanHubName(originHub.name)}! Ride $stopsCount stops to ${cleanHubName(destHub.name)}.",
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

    private fun estimateStopsCount(route: String): Int {
        return when (route.uppercase()) {
            "CN", "CS" -> 6
            "NW" -> 5
            "BB" -> 7
            "WX" -> 4
            else -> 5
        }
    }

    private fun cleanHubName(name: String): String {
        return name
            .replace("Central Campus Transit Center", "CCTC", ignoreCase = true)
            .replace("Central Campus (CCTC)", "CCTC", ignoreCase = true)
            .replace("Central Campus", "CCTC", ignoreCase = true)
            .replace("North Campus (Pierpont)", "North", ignoreCase = true)
            .replace("North Campus", "North", ignoreCase = true)
            .replace("Pierpont Commons", "North", ignoreCase = true)
            .replace("Pierpont", "North", ignoreCase = true)
            .replace(Regex("Ruthven\\s+Mue?seum?s?", RegexOption.IGNORE_CASE), "Ruthven")
            .replace(":", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun stopTrackingAndSelf() {
        isServiceRunning = false
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
        val notification = buildNotification(data)
        notificationManager.notify(1, notification)

        if (Build.VERSION.SDK_INT >= 36) {
            try {
                val canPost = notificationManager.canPostPromotedNotifications()
                Log.d("BusNotif", "Can post promoted notifications: $canPost")

                val activeNotifs = notificationManager.activeNotifications
                activeNotifs.find { it.id == 1 }?.let {
                    val isPromoted = (it.notification.flags and Notification.FLAG_PROMOTED_ONGOING) != 0
                    Log.d("BusNotif", "Notification is promoted: $isPromoted")
                }
            } catch (e: Throwable) {
                Log.e("BusNotif", "Error checking promoted status: ${e.message}")
            }
        }
    }

    private fun buildNotification(data: BusTrackingData): Notification {
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

        if (Build.VERSION.SDK_INT >= 36) {
            return buildApi36LiveNotification(data, pendingReverse, pendingStop)
        } else {
            return buildLegacyNotification(data, pendingReverse, pendingStop)
        }
    }

    @RequiresApi(36)
    private fun buildApi36LiveNotification(
        data: BusTrackingData,
        pendingReverse: PendingIntent,
        pendingStop: PendingIntent
    ): Notification {
        val extras = Bundle().apply {
            putBoolean("android.requestPromotedOngoing", true)
            putBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING, true)
        }

        val chipText = "${data.primaryRoute} ${if (data.nextBusMins <= 0) "Now" else "${data.nextBusMins}m"}"

        val contentText = data.arrivalTimeText

        val segmentColor = android.graphics.Color.parseColor("#0057B7")
        val progressStyle = Notification.ProgressStyle()
            .setProgress(data.progress)
            .setProgressSegments(listOf(
                Notification.ProgressStyle.Segment(100)
                    .setColor(segmentColor)
            ))
            .setProgressTrackerIcon(
                android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_bus_circle_white)
            )

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bus)
            .setContentTitle(data.title)
            .setContentText(contentText)
            .setSubText(data.upcomingText)
            .setShortCriticalText(chipText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_NAVIGATION)
            .setRequestPromotedOngoing(true)
            .setStyle(progressStyle)
            .addExtras(extras)
            .addAction(R.drawable.ic_action_reverse, "Reverse Route", pendingReverse)
            .addAction(R.drawable.ic_action_stop, "Stop Tracking", pendingStop)

        if (data.arrivalTimeMillis > 0L) {
            builder.setWhen(data.arrivalTimeMillis)
            builder.setShowWhen(true)
        }

        val notification = builder.build()
        notification.flags = notification.flags or Notification.FLAG_ONGOING_EVENT
        return notification
    }

    private fun buildLegacyNotification(
        data: BusTrackingData,
        pendingReverse: PendingIntent,
        pendingStop: PendingIntent
    ): Notification {
        Log.d("BusNotif", "Inflating RemoteViews for legacy fallback: ${data.title}...")

        val compactViews = RemoteViews(packageName, R.layout.notification_bus_tracker_compact).apply {
            setTextViewText(R.id.tv_route_title, data.title)
            setTextViewText(R.id.tv_eta_primary, data.primaryEtaText)
            setTextViewText(R.id.tv_arrival_time, data.arrivalTimeText)
            setProgressBar(R.id.notification_progress, 100, data.progress, false)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val maxMarginDp = 240f
                val marginDp = (data.progress / 100f * maxMarginDp).coerceIn(0f, maxMarginDp)
                setViewLayoutMargin(R.id.fl_bus_thumb, RemoteViews.MARGIN_START, marginDp, TypedValue.COMPLEX_UNIT_DIP)
            }
        }

        val expandedViews = RemoteViews(packageName, R.layout.notification_bus_tracker).apply {
            setTextViewText(R.id.tv_route_title, data.title)
            setTextViewText(R.id.tv_route_badge, data.primaryRoute)
            setTextViewText(R.id.tv_label_next, "NEXT BUS")
            setTextViewText(R.id.tv_eta_primary, data.primaryEtaText)
            setTextViewText(R.id.tv_arrival_time, data.arrivalTimeText)
            setProgressBar(R.id.notification_progress, 100, data.progress, false)
            setTextViewText(R.id.tv_upcoming, data.upcomingText)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val maxMarginDp = 280f
                val marginDp = (data.progress / 100f * maxMarginDp).coerceIn(0f, maxMarginDp)
                setViewLayoutMargin(R.id.fl_bus_thumb, RemoteViews.MARGIN_START, marginDp, TypedValue.COMPLEX_UNIT_DIP)
            }
        }

        val publicNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(data.title)
            .setContentText("${data.primaryRoute}: ${data.primaryEtaText} (${data.arrivalTimeText})")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
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
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addExtras(extras)
            .addAction(R.drawable.ic_action_reverse, "Reverse Route", pendingReverse)
            .addAction(R.drawable.ic_action_stop, "Stop Tracking", pendingStop)
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
                manager?.deleteNotificationChannel("BusTracker_Public_v5")
                manager?.deleteNotificationChannel("bus_tracker_v3")
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val channel = NotificationChannel(
                "bus_tracker_live",
                "Bus Tracker Live",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Live persistent bus tracker Live Alert status bar pill"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        job.cancel()
    }
}
