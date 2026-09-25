package com.shashankanil.nativerecorder.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import com.shashankanil.nativerecorder.R
import com.shashankanil.nativerecorder.recording.Phase
import com.shashankanil.nativerecorder.recording.RecordingSession
import com.shashankanil.nativerecorder.recording.durationLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private fun artworkSize(context: Context, id: Int): Int {
    val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
    val side = minOf(
        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 140),
        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 140),
    )
    return (side * context.resources.displayMetrics.density).toInt().coerceIn(180, 420)
}

class RecorderWidgetReceiver : AppWidgetProvider() {
    override fun onDeleted(context: Context, ids: IntArray) {
        WidgetPages.clear(context, ids)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        options: Bundle,
    ) {
        onUpdate(context, manager, intArrayOf(id))
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                RecorderWidget.refresh(context)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Home-screen widget publisher. RemoteViews + bitmap artwork (Glance dropped for visual/swipe fidelity). */
object RecorderWidget {
    private val renderLock = Mutex()

    suspend fun refresh(context: Context) = renderLock.withLock {
        withContext(Dispatchers.IO) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RecorderWidgetReceiver::class.java))
            if (ids.isEmpty()) return@withContext
            val state = RecordingSession.state.value
            if (state.phase != Phase.IDLE) {
                // Keep live pages glanceable when a session is running.
            }
            ids.forEach { id ->
                val kind = WidgetPages.kind(context, id)
                val size = artworkSize(context, id)
                val views = RemoteViews(context.packageName, R.layout.recorder_widget).apply {
                    setImageViewBitmap(R.id.recorder_page, WidgetArtwork.render(kind, state, size))
                    setContentDescription(R.id.recorder_page, description(kind, state))
                    setOnClickPendingIntent(R.id.recorder_page, swipeIntent(context, id))
                }
                manager.updateAppWidget(id, views)
            }
        }
    }

    private fun description(kind: WidgetPages.Kind, state: com.shashankanil.nativerecorder.recording.RecordingState): String {
        val status = when (state.phase) {
            Phase.IDLE -> "Idle"
            Phase.RECORDING -> "Recording ${durationLabel(state.elapsedMs)}"
            Phase.PAUSED -> "Paused ${durationLabel(state.elapsedMs)}"
            Phase.SAVING -> "Saving"
        }
        return "${kind.title}. $status. Flick up or down through waveform, timer and action. Tap to record or confirm stop."
    }

    private fun swipeIntent(context: Context, id: Int): PendingIntent {
        val intent = Intent(context, WidgetSwipeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            data = Uri.parse("nativerecorder://widget-swipe/$id")
        }
        return PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
