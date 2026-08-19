package io.github.secondjb.annarboard.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.GlanceTheme
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

import android.content.Intent
import androidx.glance.LocalContext
import androidx.glance.appwidget.action.actionStartService
import io.github.secondjb.annarboard.R
import io.github.secondjb.annarboard.service.TrackingService
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.ColorFilter

class MBusWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                val prefs = currentState<Preferences>()
                WidgetContent(prefs)
            }
        }
    }

    @Composable
    private fun WidgetContent(prefs: Preferences) {
        val campus = prefs[MBusWidgetKeys.CAMPUS] ?: "central_to_north"
        val json = prefs[MBusWidgetKeys.ETAS_JSON]
        val lastUpdated = prefs[MBusWidgetKeys.LAST_UPDATED] ?: 0L
        val isLoading = prefs[MBusWidgetKeys.IS_LOADING] ?: false
        val errorMessage = prefs[MBusWidgetKeys.ERROR_MESSAGE]
        val useAbsoluteTime = prefs[MBusWidgetKeys.USE_ABSOLUTE_TIME] ?: false

        val isNorthToCentral = campus.equals("north_to_central", ignoreCase = true)
        val titleText = if (isNorthToCentral) "North → CCTC" else "CCTC → North"

        val context = LocalContext.current
        val (originKey, destKey) = if (isNorthToCentral) "pierpont" to "cctc" else "cctc" to "pierpont"
        val startTrackingIntent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START_TRACKING
            putExtra(TrackingService.EXTRA_ORIGIN_HUB, originKey)
            putExtra(TrackingService.EXTRA_DESTINATION_HUB, destKey)
        }

        val arrivals: List<BusArrivalItem> = if (!json.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<List<BusArrivalItem>>() {}.type
                Gson().fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(16.dp)
                .padding(12.dp)
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize()
            ) {
                // Header Row
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = titleText,
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurface,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = formatTimestamp(lastUpdated, isLoading),
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                    }

                    // Mode Toggle (Relative 5m vs Absolute 7:28)
                    Box(
                        modifier = GlanceModifier
                            .background(GlanceTheme.colors.surfaceVariant)
                            .cornerRadius(12.dp)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .clickable(actionRunCallback<ToggleTimeFormatAction>()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (useAbsoluteTime) "Clock" else "Relative",
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.width(6.dp))

                    // Track Live Action Icon Button
                    Box(
                        modifier = GlanceModifier
                            .background(GlanceTheme.colors.primary)
                            .cornerRadius(12.dp)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .clickable(actionRunCallback<StartTrackingAction>()),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_notification_bell),
                            contentDescription = "Track Live Notification",
                            modifier = GlanceModifier.size(16.dp),
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary)
                        )
                    }

                    Spacer(modifier = GlanceModifier.width(6.dp))

                    // Refresh Icon Button
                    Box(
                        modifier = GlanceModifier
                            .background(GlanceTheme.colors.secondaryContainer)
                            .cornerRadius(12.dp)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .clickable(actionRunCallback<RefreshAction>()),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(android.R.drawable.ic_popup_sync),
                            contentDescription = "Refresh",
                            modifier = GlanceModifier.size(16.dp),
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer)
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.height(10.dp))

                // Content Body
                if (errorMessage != null) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .defaultWeight()
                            .background(GlanceTheme.colors.errorContainer)
                            .cornerRadius(10.dp)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Error: $errorMessage",
                            style = TextStyle(color = GlanceTheme.colors.onErrorContainer, fontSize = 12.sp)
                        )
                    }
                } else if (arrivals.isEmpty()) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .defaultWeight()
                            .background(GlanceTheme.colors.surfaceVariant)
                            .cornerRadius(10.dp)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (lastUpdated == 0L) "Tap Refresh to load ETAs" else "No upcoming buses found",
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp)
                        )
                    }
                } else {
                    Column(
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                    ) {
                        arrivals.take(4).forEachIndexed { index, bus ->
                            if (index > 0) {
                                Spacer(modifier = GlanceModifier.height(6.dp))
                            }
                            BusRowItem(bus, useAbsoluteTime, lastUpdated)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun BusRowItem(
        bus: BusArrivalItem,
        useAbsoluteTime: Boolean,
        lastUpdated: Long
    ) {
        val etaText = if (useAbsoluteTime) {
            val baseTime = if (lastUpdated > 0) lastUpdated else System.currentTimeMillis()
            val arrivalMillis = baseTime + bus.minutes * 60 * 1000L
            val sdf = SimpleDateFormat("h:mm", Locale.getDefault())
            sdf.format(Date(arrivalMillis))
        } else {
            if (bus.minutes == 0) "NOW" else "${bus.minutes}m"
        }

        val etaBg = if (bus.minutes <= 2) GlanceTheme.colors.error else GlanceTheme.colors.primaryContainer
        val etaTextColor = if (bus.minutes <= 2) GlanceTheme.colors.onError else GlanceTheme.colors.onPrimaryContainer

        val cleanStop = bus.stopName
            .replace("Central Campus Transit Center", "CCTC", ignoreCase = true)
            .replace("Pierpont Commons", "Pierpont", ignoreCase = true)

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(GlanceTheme.colors.surfaceVariant)
                .cornerRadius(10.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Fixed width Route Pill for aligned vertical stop names
            Box(
                modifier = GlanceModifier
                    .width(42.dp)
                    .background(GlanceTheme.colors.primary)
                    .cornerRadius(6.dp)
                    .padding(vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = bus.route,
                    style = TextStyle(
                        color = GlanceTheme.colors.onPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            // Stop / Dest Name
            Text(
                text = cleanStop,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )

            Spacer(modifier = GlanceModifier.width(8.dp))

            // Countdown Pill
            Box(
                modifier = GlanceModifier
                    .background(etaBg)
                    .cornerRadius(6.dp)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = etaText,
                    style = TextStyle(
                        color = etaTextColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }

    private fun formatTimestamp(timestamp: Long, isLoading: Boolean): String {
        if (isLoading) return "Updating..."
        if (timestamp == 0L) return "Not updated yet"

        val nowCal = Calendar.getInstance()
        val updatedCal = Calendar.getInstance().apply { timeInMillis = timestamp }

        val isSameDay = nowCal.get(Calendar.YEAR) == updatedCal.get(Calendar.YEAR) &&
                nowCal.get(Calendar.DAY_OF_YEAR) == updatedCal.get(Calendar.DAY_OF_YEAR)

        return if (isSameDay) {
            val sdf = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
            "Updated at ${sdf.format(Date(timestamp))}"
        } else {
            val sdf = SimpleDateFormat("MMM d, h:mm:ss a", Locale.getDefault())
            "Updated ${sdf.format(Date(timestamp))}"
        }
    }
}
