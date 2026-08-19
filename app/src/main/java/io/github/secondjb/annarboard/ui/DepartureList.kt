package io.github.secondjb.annarboard.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import io.github.secondjb.annarboard.theme.LocalSettingsManager

data class DepartureInfo(
    val time: String,
    val busLine: String,
    val location: String,
    val system: String
)

@Composable
fun DepartureList(departures: List<DepartureInfo>, loading: Boolean, modifier: Modifier = Modifier) {
    val settingsManager = LocalSettingsManager.current
    val settings = settingsManager.currentSettings.value

    val filtered = departures.filter {
        it.system == "MBus" && settings.mbusEnabled
    }

    val displayList = filtered.take(settings.maxBusesDepartureList * 2).sortedBy { it.time }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Upcoming Departures",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            if (displayList.isEmpty()) {
                Text("No upcoming departures.", modifier = Modifier.padding(16.dp), color = Color.Gray)
            } else {
                displayList.forEach { dep ->
                    DepartureRow(dep, settings.use24HourClock)
                }
            }
        }
    }
}

@Composable
fun DepartureRow(departure: DepartureInfo, use24HourClock: Boolean) {
    val formattedTime = if (use24HourClock || !departure.time.contains(":")) departure.time else {
        val parts = departure.time.split(":")
        var h = parts[0].toIntOrNull() ?: 12
        val ampm = if (h >= 12) "PM" else "AM"
        h = if (h % 12 == 0) 12 else h % 12
        "$h:${parts[1]} $ampm"
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.DirectionsBus, contentDescription = "Bus", tint = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                val displayLocation = cleanLocationName(departure.location)
                Text(departure.busLine, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("at $displayLocation", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center
        ) {
            val parts = formattedTime.split(" ")
            if (parts.size == 2) {
                Text(
                    text = parts[0],
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.primary,
                    lineHeight = 22.sp
                )
                Text(
                    text = parts[1],
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    lineHeight = 11.sp
                )
            } else {
                Text(
                    text = formattedTime,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
