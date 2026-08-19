package io.github.secondjb.annarboard.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
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

        val intent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START_TRACKING
            putExtra(TrackingService.EXTRA_ORIGIN_HUB, origin)
            putExtra(TrackingService.EXTRA_DESTINATION_HUB, dest)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
