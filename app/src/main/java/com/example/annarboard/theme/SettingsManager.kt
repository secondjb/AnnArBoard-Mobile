package com.example.annarboard.theme

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf

data class AppSettings(
    val updateFrequency: Int = 15,
    val updateFrequencyMBus: Int = 15,
    val isSplitFrequency: Boolean = false,
    val mbusEnabled: Boolean = true,
    val updateOnMobileData: Boolean = true,
    val appTheme: String = "dynamic",
    val actionBoardColorMode: String = "theme",
    val showActionBoard: Boolean = true,
    val showUpcomingDepartures: Boolean = true,
    val showMainLogo: Boolean = true,
    val showLiveTrackingFab: Boolean = true,
    val showPaletteIcon: Boolean = true,
    val showFullscreenIcon: Boolean = true,
    val showLocationIcons: Boolean = true,
    val use24HourClock: Boolean = false,
    val showActionLegend: Boolean = true,
    val showDepartureLegend: Boolean = true,
    val showIntegrateSplitSwitch: Boolean = true,
    val maxBusesActionBoard: Int = 5,
    val maxBusesDepartureList: Int = 10,
    val showGlobalStaleWarning: Boolean = true,
    val showAsterisksMBus: Boolean = true,
    val showSyncTime: Boolean = true,
    val showSyncText: Boolean = true,
    val animateSyncIcon: Boolean = true,
    val showSyncIcon: Boolean = true,
    val simulateData: Boolean = false
)

class SettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var currentSettings = mutableStateOf(loadSettings())
        private set

    private fun loadSettings(): AppSettings {
        return AppSettings(
            updateFrequency = prefs.getInt("updateFrequency", 15),
            updateFrequencyMBus = prefs.getInt("updateFrequencyMBus", 15),
            isSplitFrequency = prefs.getBoolean("isSplitFrequency", false),
            mbusEnabled = prefs.getBoolean("mbusEnabled", true),
            updateOnMobileData = prefs.getBoolean("updateOnMobileData", true),
            appTheme = prefs.getString("appTheme", "dynamic") ?: "dynamic",
            actionBoardColorMode = prefs.getString("actionBoardColorMode", "theme") ?: "theme",
            showActionBoard = prefs.getBoolean("showActionBoard", true),
            showUpcomingDepartures = prefs.getBoolean("showUpcomingDepartures", true),
            showMainLogo = prefs.getBoolean("showMainLogo", true),
            showLiveTrackingFab = prefs.getBoolean("showLiveTrackingFab", true),
            showPaletteIcon = prefs.getBoolean("showPaletteIcon", true),
            showFullscreenIcon = prefs.getBoolean("showFullscreenIcon", true),
            showLocationIcons = prefs.getBoolean("showLocationIcons", true),
            use24HourClock = prefs.getBoolean("use24HourClock", false),
            showActionLegend = prefs.getBoolean("showActionLegend", true),
            showDepartureLegend = prefs.getBoolean("showDepartureLegend", true),
            showIntegrateSplitSwitch = prefs.getBoolean("showIntegrateSplitSwitch", true),
            maxBusesActionBoard = prefs.getInt("maxBusesActionBoard", 5),
            maxBusesDepartureList = prefs.getInt("maxBusesDepartureList", 10),
            showGlobalStaleWarning = prefs.getBoolean("showGlobalStaleWarning", true),
            showAsterisksMBus = prefs.getBoolean("showAsterisksMBus", true),
            showSyncTime = prefs.getBoolean("showSyncTime", true),
            showSyncText = prefs.getBoolean("showSyncText", true),
            animateSyncIcon = prefs.getBoolean("animateSyncIcon", true),
            showSyncIcon = prefs.getBoolean("showSyncIcon", true),
            simulateData = prefs.getBoolean("simulateData", false)
        )
    }

    fun updateSetting(updater: (AppSettings) -> AppSettings) {
        val newSettings = updater(currentSettings.value)
        currentSettings.value = newSettings
        saveSettings(newSettings)
    }

    private fun saveSettings(settings: AppSettings) {
        prefs.edit().apply {
            putInt("updateFrequency", settings.updateFrequency)
            putInt("updateFrequencyMBus", settings.updateFrequencyMBus)
            putBoolean("isSplitFrequency", settings.isSplitFrequency)
            putBoolean("mbusEnabled", settings.mbusEnabled)
            putBoolean("updateOnMobileData", settings.updateOnMobileData)
            putString("appTheme", settings.appTheme)
            putString("actionBoardColorMode", settings.actionBoardColorMode)
            putBoolean("showActionBoard", settings.showActionBoard)
            putBoolean("showUpcomingDepartures", settings.showUpcomingDepartures)
            putBoolean("showMainLogo", settings.showMainLogo)
            putBoolean("showLiveTrackingFab", settings.showLiveTrackingFab)
            putBoolean("showPaletteIcon", settings.showPaletteIcon)
            putBoolean("showFullscreenIcon", settings.showFullscreenIcon)
            putBoolean("showLocationIcons", settings.showLocationIcons)
            putBoolean("use24HourClock", settings.use24HourClock)
            putBoolean("showActionLegend", settings.showActionLegend)
            putBoolean("showDepartureLegend", settings.showDepartureLegend)
            putBoolean("showIntegrateSplitSwitch", settings.showIntegrateSplitSwitch)
            putInt("maxBusesActionBoard", settings.maxBusesActionBoard)
            putInt("maxBusesDepartureList", settings.maxBusesDepartureList)
            putBoolean("showGlobalStaleWarning", settings.showGlobalStaleWarning)
            putBoolean("showAsterisksMBus", settings.showAsterisksMBus)
            putBoolean("showSyncTime", settings.showSyncTime)
            putBoolean("showSyncText", settings.showSyncText)
            putBoolean("animateSyncIcon", settings.animateSyncIcon)
            putBoolean("showSyncIcon", settings.showSyncIcon)
            putBoolean("simulateData", settings.simulateData)
            apply()
        }
    }
}

val LocalSettingsManager = compositionLocalOf<SettingsManager> { error("No SettingsManager provided") }
