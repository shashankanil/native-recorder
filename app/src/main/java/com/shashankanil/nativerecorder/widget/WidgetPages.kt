package com.shashankanil.nativerecorder.widget

import android.content.Context
import com.shashankanil.nativerecorder.recording.Phase
import com.shashankanil.nativerecorder.recording.RecordingSession

/** Persists the square widget page index per appWidgetId. */
internal object WidgetPages {
    enum class Kind(val title: String) {
        WAVEFORM("Waveform"),
        TIMER("Timer"),
        ACTION("Action"),
    }

    val kinds = Kind.entries
    private const val PREFS = "recorder_widget_pages"
    private fun key(id: Int) = "page_$id"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun index(context: Context, appWidgetId: Int): Int {
        val raw = prefs(context).getInt(key(appWidgetId), 0)
        return raw.floorMod(kinds.size)
    }

    fun kind(context: Context, appWidgetId: Int): Kind = kinds[index(context, appWidgetId)]

    fun advance(context: Context, appWidgetId: Int, delta: Int): Int {
        val next = (index(context, appWidgetId) + delta).floorMod(kinds.size)
        prefs(context).edit().putInt(key(appWidgetId), next).apply()
        return next
    }

    fun setKind(context: Context, appWidgetId: Int, kind: Kind) {
        prefs(context).edit().putInt(key(appWidgetId), kinds.indexOf(kind).coerceAtLeast(0)).apply()
    }

    /** When a session starts, snap every instance to the live waveform page. */
    fun snapActiveToWaveform(context: Context, ids: IntArray) {
        if (RecordingSession.state.value.phase == Phase.IDLE || ids.isEmpty()) return
        prefs(context).edit().apply {
            ids.forEach { putInt(key(it), kinds.indexOf(Kind.WAVEFORM)) }
        }.apply()
    }

    fun clear(context: Context, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        prefs(context).edit().apply {
            appWidgetIds.forEach { remove(key(it)) }
        }.apply()
    }

    private fun Int.floorMod(m: Int): Int = ((this % m) + m) % m
}
