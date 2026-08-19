package com.example.annarboard.widget

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MBusWidgetKeys {
    val CAMPUS = stringPreferencesKey("widget_campus")
    val ETAS_JSON = stringPreferencesKey("widget_etas_json")
    val LAST_UPDATED = longPreferencesKey("widget_last_updated")
    val IS_LOADING = booleanPreferencesKey("widget_is_loading")
    val ERROR_MESSAGE = stringPreferencesKey("widget_error_message")
    val USE_ABSOLUTE_TIME = booleanPreferencesKey("widget_use_absolute_time")
}

class ToggleTimeFormatAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            val current = prefs[MBusWidgetKeys.USE_ABSOLUTE_TIME] ?: false
            prefs.toMutablePreferences().apply {
                this[MBusWidgetKeys.USE_ABSOLUTE_TIME] = !current
            }
        }
        MBusWidget().update(context, glanceId)
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // Set loading state
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[MBusWidgetKeys.IS_LOADING] = true
                this.remove(MBusWidgetKeys.ERROR_MESSAGE)
            }
        }
        MBusWidget().update(context, glanceId)

        withContext(Dispatchers.IO) {
            try {
                val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
                val campus = prefs[MBusWidgetKeys.CAMPUS] ?: "central_to_north"

                val arrivals = MBusWidgetRepository.fetchArrivals(campus)
                val json = Gson().toJson(arrivals)

                updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[MBusWidgetKeys.ETAS_JSON] = json
                        this[MBusWidgetKeys.LAST_UPDATED] = System.currentTimeMillis()
                        this[MBusWidgetKeys.IS_LOADING] = false
                        this.remove(MBusWidgetKeys.ERROR_MESSAGE)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[MBusWidgetKeys.IS_LOADING] = false
                        this[MBusWidgetKeys.ERROR_MESSAGE] = e.localizedMessage ?: "Failed to update"
                    }
                }
            }
        }

        MBusWidget().update(context, glanceId)
    }
}
