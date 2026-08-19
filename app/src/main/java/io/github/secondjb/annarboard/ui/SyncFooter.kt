package io.github.secondjb.annarboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SyncDisabled
import io.github.secondjb.annarboard.theme.LocalSettingsManager
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SyncFooter(lastUpdateTime: Long, pausedForMobileData: Boolean) {
    val settingsManager = LocalSettingsManager.current
    val settings = settingsManager.currentSettings.value

    var currentTime by remember { mutableStateOf(Date()) }
    
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Date()
            delay(1000)
        }
    }

    val pattern = if (settings.use24HourClock) "HH:mm:ss" else "hh:mm:ss a"
    val timeString = SimpleDateFormat(pattern, Locale.getDefault()).format(currentTime)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp)
            .background(Color.Black.copy(alpha = 0.02f), RoundedCornerShape(16.dp))
            .border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (settings.showSyncTime) {
            Text(timeString, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
        }

        if (settings.showSyncText && settings.mbusEnabled) {
            val secondsSinceUpdate = ((currentTime.time - lastUpdateTime) / 1000).coerceAtLeast(0)
            val updateText = if (pausedForMobileData) "Paused (Cellular)" else if (secondsSinceUpdate <= 0) "Updated just now" else "Updated ${secondsSinceUpdate}s ago"
            val textColor = if (pausedForMobileData || secondsSinceUpdate > 30) MaterialTheme.colorScheme.error else Color.Unspecified
            Text(updateText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 4.dp), color = textColor)
        }
        
        if (settings.showSyncIcon) {
            val icon = if (pausedForMobileData) Icons.Default.SyncDisabled else Icons.Default.Refresh
            val iconTint = if (pausedForMobileData) MaterialTheme.colorScheme.error else LocalContentColor.current
            Icon(icon, contentDescription = "Sync", modifier = Modifier.size(16.dp).padding(start = 4.dp), tint = iconTint)
        }
    }
}
