package com.example.annarboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


data class BusAlert(
    val leaveInMinutes: Int,
    val bus: String,
    val location: String,
    val system: String = "MBus"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActionBoard(
    alerts: List<BusAlert>,
    loading: Boolean,
    modifier: Modifier = Modifier,
    onStartTracking: ((BusAlert) -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (loading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "UPDATING LIVE DATA...",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            alerts.forEachIndexed { index, alert ->
                ActionBoardCard(alert = alert, index = index, onStartTracking = onStartTracking)
            }
        }
    }
}

// 1. Exact replication of MUI's lighten algorithm
fun Color.muiLighten(coefficient: Float): Color {
    val r = this.red + (1f - this.red) * coefficient
    val g = this.green + (1f - this.green) * coefficient
    val b = this.blue + (1f - this.blue) * coefficient
    return Color(r, g, b, this.alpha)
}

// 2. Exact replication of MUI's darken algorithm
fun Color.muiDarken(coefficient: Float): Color {
    val r = this.red * (1f - coefficient)
    val g = this.green * (1f - coefficient)
    val b = this.blue * (1f - coefficient)
    return Color(r, g, b, this.alpha)
}

// 3. Exact replication of MUI's getContrastText
fun getMuiContrastText(bg: Color): Color {
    // MUI uses a contrast threshold; simplified here via luminance to output MUI's standard text colors
    return if (bg.luminance() > 0.5f) Color.Black.copy(alpha = 0.87f) else Color.White
}

@Composable
fun ActionBoardCard(
    alert: BusAlert,
    index: Int,
    onStartTracking: ((BusAlert) -> Unit)? = null
) {
    val baseColor = MaterialTheme.colorScheme.primary
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // Identical bg logic to Web
    val bg = when (index) {
        0 -> baseColor
        1 -> if (isDark) baseColor.muiDarken(0.3f) else baseColor.muiLighten(0.3f)
        else -> if (isDark) baseColor.muiDarken(0.6f) else baseColor.muiLighten(0.6f)
    }

    val textColor = getMuiContrastText(bg)

    // Web uses theme.palette.primary.light. MUI dynamically creates this by lightening the main color by ~20-30%
    val lightColor = baseColor.muiLighten(0.25f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(12.dp))
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = bg)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(if (isDark) Color.Black.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.6f), CircleShape)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(lightColor)
                            .shadow(elevation = 12.dp, shape = CircleShape, ambientColor = lightColor, spotColor = lightColor)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val s = if (alert.leaveInMinutes != 1) "s" else ""
                    Text(
                        text = "Arriving in ${alert.leaveInMinutes} min$s for ${alert.bus}",
                        color = textColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "at ${alert.location}",
                        color = textColor.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    )
                }
            }
        }

        if (onStartTracking != null) {
            IconButton(
                onClick = { onStartTracking(alert) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(36.dp)
                    .background(textColor.copy(alpha = 0.18f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsBus,
                    contentDescription = "Track Live",
                    tint = textColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActionBoardLegend() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .background(Color.Black.copy(alpha = 0.02f), RoundedCornerShape(12.dp))
            .border(1.dp, Color.LightGray.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LegendItem(Color(0xFF00E676), "Arriving Now (≤ 5 min)")
            LegendItem(Color(0xFFFF9100), "Arriving Soon (≤ 10 min)")
            LegendItem(Color(0xFFFF1744), "Arriving Later (> 10 min)")
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, CircleShape)
                .shadow(elevation = 4.dp, shape = CircleShape, spotColor = color)
        )
        Text(text = label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.Gray)
    }
}
