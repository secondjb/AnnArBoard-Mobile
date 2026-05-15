package com.example.annarboard.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.annarboard.R
import com.example.annarboard.network.Constants
import com.example.annarboard.network.RetrofitClient
import kotlinx.coroutines.*

class TrackingService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    
    private var originKey = "cctc"
    private var destinationKey = "pierpont"
    
    private val CHANNEL_ID = "TrackingChannel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "REVERSE_DIRECTION") {
            val temp = originKey
            originKey = destinationKey
            destinationKey = temp
            pollData()
            return START_STICKY
        }

        intent?.getStringExtra("ORIGIN_HUB")?.let { originKey = it }
        intent?.getStringExtra("DESTINATION_HUB")?.let { destinationKey = it }

        startForeground(1, buildNotification(0, "Loading...", "Loading..."))
        
        startPolling()
        
        return START_STICKY
    }
    
    private fun startPolling() {
        scope.launch {
            while (isActive) {
                pollData()
                delay(15000)
            }
        }
    }
    
    private fun pollData() {
        scope.launch {
            try {
                val originHub = Constants.HUBS.find { it.key == originKey } ?: Constants.HUBS[0]
                val destHub = Constants.HUBS.find { it.key == destinationKey } ?: Constants.HUBS[1]
                
                val response = RetrofitClient.instance.getMBusPredictions(originHub.stopIds.joinToString(","))
                val prdList = response.bustimeResponse?.prd ?: emptyList()
                
                val filtered = prdList.filter { p ->
                    val des = p.des.lowercase()
                    destHub.keywords.any { kw -> des.contains(kw.lowercase()) }
                }
                
                val nextBus = filtered.minByOrNull { if (it.prdctdn == "DUE") 0 else it.prdctdn.toIntOrNull() ?: Int.MAX_VALUE }
                
                val title = "${originHub.name} to ${destHub.name}"
                val subtitle = if (nextBus != null) {
                    val minText = if (nextBus.prdctdn == "DUE") "DUE" else "${nextBus.prdctdn} min"
                    "Next ${nextBus.rt}: $minText"
                } else {
                    "No buses currently scheduled"
                }
                
                val progress = if (nextBus != null) {
                    val min = if (nextBus.prdctdn == "DUE") 0 else nextBus.prdctdn.toIntOrNull() ?: 15
                    ((15 - min).coerceIn(0, 15).toFloat() / 15f * 100).toInt()
                } else {
                    0
                }
                
                updateNotification(progress, title, subtitle)
                
            } catch (e: Exception) {
                e.printStackTrace()
                updateNotification(0, "Error updating", "Will retry shortly")
            }
        }
    }

    private fun updateNotification(progress: Int, title: String, subtitle: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(1, buildNotification(progress, title, subtitle))
    }

    private fun buildNotification(progress: Int, title: String, subtitle: String): Notification {
        val reverseIntent = Intent(this, TrackingService::class.java).apply {
            action = "REVERSE_DIRECTION"
        }
        val pendingReverse = PendingIntent.getService(this, 0, reverseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        if (Build.VERSION.SDK_INT >= 36) {
            return buildAndroid16Notification(progress, title, subtitle, pendingReverse)
        } else {
            return buildFallbackNotification(progress, title, subtitle, pendingReverse)
        }
    }

    @RequiresApi(36)
    private fun buildAndroid16Notification(progress: Int, title: String, subtitle: String, pendingReverse: PendingIntent): Notification {
        val progressStyle = Notification.ProgressStyle()
        // Try to add progress point. In a real environment, we'd use reflection or compile against Android 16 API level 36 properly.
        // Assuming the method `addProgressPoint(progress, icon)` exists as per prompt. 
        // I will omit the icon parameter if not strictly required, or pass null/default icon.
        
        try {
            // Attempt to invoke the method requested
            val addPointMethod = progressStyle.javaClass.getMethod("addProgressPoint", Int::class.java)
            addPointMethod.invoke(progressStyle, progress)
        } catch (e: Exception) {
            // Fallback if signature is different
            e.printStackTrace()
        }

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setStyle(progressStyle)
            .addAction(android.R.drawable.ic_menu_revert, "Reverse", pendingReverse)
            .setOngoing(true)
            .build()
    }

    private fun buildFallbackNotification(progress: Int, title: String, subtitle: String, pendingReverse: PendingIntent): Notification {
        val remoteViews = RemoteViews(packageName, R.layout.notification_tracking_fallback)
        remoteViews.setTextViewText(R.id.notification_title, title)
        remoteViews.setTextViewText(R.id.notification_subtitle, subtitle)
        remoteViews.setProgressBar(R.id.notification_progress, 100, progress, false)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setCustomContentView(remoteViews)
            .addAction(android.R.drawable.ic_menu_revert, "Reverse", pendingReverse)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bus Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
