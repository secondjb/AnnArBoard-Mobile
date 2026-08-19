package io.github.secondjb.annarboard.widget

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import io.github.secondjb.annarboard.MainActivity
import io.github.secondjb.annarboard.service.TrackingService

class StartTrackingAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        val campus = prefs[MBusWidgetKeys.CAMPUS] ?: "central_to_north"
        val (origin, dest) = if (campus.equals("north_to_central", ignoreCase = true)) {
            "pierpont" to "cctc"
        } else {
            "cctc" to "pierpont"
        }

        val hasNotificationPermission = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (!hasNotificationPermission) {
            Log.d("StartTrackingAction", "Notification permission not granted. Launching MainActivity to request permission.")
            val activityIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("EXTRA_REQUEST_TRACKING_PERMISSION", true)
                putExtra(TrackingService.EXTRA_ORIGIN_HUB, origin)
                putExtra(TrackingService.EXTRA_DESTINATION_HUB, dest)
            }
            context.startActivity(activityIntent)
            return
        }

        val serviceIntent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START_TRACKING
            putExtra(TrackingService.EXTRA_ORIGIN_HUB, origin)
            putExtra(TrackingService.EXTRA_DESTINATION_HUB, dest)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.w("StartTrackingAction", "Failed to start foreground service directly from widget. Falling back to MainActivity.", e)
            val activityIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("EXTRA_REQUEST_TRACKING_PERMISSION", true)
                putExtra(TrackingService.EXTRA_ORIGIN_HUB, origin)
                putExtra(TrackingService.EXTRA_DESTINATION_HUB, dest)
            }
            context.startActivity(activityIntent)
        }
    }
}
