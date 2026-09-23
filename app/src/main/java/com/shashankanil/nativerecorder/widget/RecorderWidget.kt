package com.shashankanil.nativerecorder.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.*
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.layout.*
import androidx.glance.material3.ColorProviders
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.shashankanil.nativerecorder.MainActivity
import com.shashankanil.nativerecorder.recording.*
import com.shashankanil.nativerecorder.service.RecordingForegroundService
import com.shashankanil.nativerecorder.ui.theme.DarkColors
import com.shashankanil.nativerecorder.ui.theme.LightColors

class RecorderWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme(colors = if (Build.VERSION.SDK_INT >= 31) GlanceTheme.colors else ColorProviders(light = LightColors, dark = DarkColors)) {
                Content(context)
            }
        }
    }
    @Composable
    private fun Content(context: Context) {
        val status = currentState<androidx.datastore.preferences.core.Preferences>()[STATUS] ?: "Idle"
        val active = status != "Idle"
        Column(GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Recorder", style = TextStyle(color = GlanceTheme.colors.onSurface))
            Spacer(GlanceModifier.height(8.dp))
            Text("Status: $status", style = TextStyle(color = GlanceTheme.colors.onSurface))
            Spacer(GlanceModifier.height(8.dp))
            if (active) {
                Button(if (status == "Saving") "Saving…" else "Stop", onClick = actionRunCallback<StopRecordingAction>(), enabled = status != "Saving")
            } else {
                Button(if (Build.VERSION.SDK_INT >= 29) "Record device audio" else "Open microphone recorder",
                    onClick = actionStartActivity(Intent(context, MainActivity::class.java)
                        .setAction(MainActivity.WIDGET_RECORD).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)))
            }
        }
    }
    companion object {
        val STATUS = stringPreferencesKey("status")
        suspend fun refresh(context: Context) {
            val widget = RecorderWidget()
            GlanceAppWidgetManager(context).getGlanceIds(RecorderWidget::class.java).forEach { id ->
                updateAppWidgetState(context, id) { prefs ->
                    prefs[STATUS] = RecordingSession.state.value.phase.name.lowercase().replaceFirstChar { it.uppercase() }
                }
                widget.update(context, id)
            }
        }
    }
}
class RecorderWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = RecorderWidget() }
class StopRecordingAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        if (RecordingSession.state.value.phase != Phase.IDLE) {
            context.startService(Intent(context, RecordingForegroundService::class.java).setAction(RecordingForegroundService.STOP))
        } else RecorderWidget.refresh(context)
    }
}
