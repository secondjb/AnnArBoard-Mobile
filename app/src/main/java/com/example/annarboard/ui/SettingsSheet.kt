package com.example.annarboard.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.annarboard.theme.LocalSettingsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(onDismissRequest: () -> Unit) {
    val settingsManager = LocalSettingsManager.current
    val settings = settingsManager.currentSettings.value

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Control Center",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                )
                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Default.Close, contentDescription = "Close Settings")
                }
            }

            SectionHeader("Application Theme")
            ThemeSelector(modifier = Modifier.padding(bottom = 24.dp))

            SectionHeader("Refresh Rate")
            SettingSliderRow("Global Refresh Rate", settings.updateFrequency, 3f, 120f) {
                settingsManager.updateSetting { s -> s.copy(updateFrequency = it.toInt()) }
            }
            Divider(Modifier.padding(vertical = 16.dp))

            SectionHeader("Active Transit Systems")
            SettingSwitchRow("University MBus", settings.mbusEnabled) { settingsManager.updateSetting { s -> s.copy(mbusEnabled = it) } }
            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            SectionHeader("Network")
            SettingSwitchRow("Update on Mobile Data", settings.updateOnMobileData) { settingsManager.updateSetting { s -> s.copy(updateOnMobileData = it) } }
            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            SectionHeader("Component Visibility")
            SettingSwitchRow("Show Action Board", settings.showActionBoard) { settingsManager.updateSetting { s -> s.copy(showActionBoard = it) } }
            SettingSwitchRow("Show Upcoming Departures", settings.showUpcomingDepartures) { settingsManager.updateSetting { s -> s.copy(showUpcomingDepartures = it) } }
            SettingSwitchRow("Show Ann Ar-Board Logo", settings.showMainLogo) { settingsManager.updateSetting { s -> s.copy(showMainLogo = it) } }
            SettingSwitchRow("Show Live Tracking FAB", settings.showLiveTrackingFab) { settingsManager.updateSetting { s -> s.copy(showLiveTrackingFab = it) } }
            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            SectionHeader("Data Legibility")
            SettingSwitchRow("Use 24-Hour Clock", settings.use24HourClock) { settingsManager.updateSetting { s -> s.copy(use24HourClock = it) } }
            SettingSwitchRow("Show Integrate/Split Switch", settings.showIntegrateSplitSwitch) { settingsManager.updateSetting { s -> s.copy(showIntegrateSplitSwitch = it) } }
            Divider(Modifier.padding(vertical = 16.dp))

            SectionHeader("Display Limits")
            SettingSliderRow("Action Board Max Items", settings.maxBusesActionBoard, 1f, 10f) { settingsManager.updateSetting { s -> s.copy(maxBusesActionBoard = it.toInt()) } }
            SettingSliderRow("Departure List Max Items", settings.maxBusesDepartureList, 1f, 15f) { settingsManager.updateSetting { s -> s.copy(maxBusesDepartureList = it.toInt()) } }
            Divider(Modifier.padding(vertical = 16.dp))

            SectionHeader("Sync Tracker Display")
            SettingSwitchRow("Show Sync Time", settings.showSyncTime) { settingsManager.updateSetting { s -> s.copy(showSyncTime = it) } }
            SettingSwitchRow("Show Sync Text", settings.showSyncText) { settingsManager.updateSetting { s -> s.copy(showSyncText = it) } }
            SettingSwitchRow("Show Sync Icon", settings.showSyncIcon) { settingsManager.updateSetting { s -> s.copy(showSyncIcon = it) } }
            Divider(Modifier.padding(vertical = 16.dp))

            SectionHeader("Developer Settings")
            SettingSwitchRow("Simulate Live Data", settings.simulateData) { settingsManager.updateSetting { s -> s.copy(simulateData = it) } }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun SettingSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingSliderRow(label: String, value: Int, min: Float, max: Float, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("$label: $value", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Slider(
            value = value.toFloat(),
            onValueChange = onValueChange,
            valueRange = min..max
        )
    }
}
