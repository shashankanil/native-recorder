package com.shashankanil.nativerecorder.recording

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

enum class Source(val label: String) { MIC("Mic"), DEVICE("Device") }
enum class Phase { IDLE, RECORDING, PAUSED, SAVING }
data class RecordingState(val phase: Phase = Phase.IDLE, val source: Source = Source.MIC, val elapsedMs: Long = 0)
data class Notice(val text: String, val settings: Boolean = false)

/** Process-local bridge; the service owns the session, never the Activity. */
object RecordingSession {
    val state = MutableStateFlow(RecordingState())
    val libraryVersion = MutableStateFlow(0)
    private val messages = Channel<Notice>(Channel.UNLIMITED)
    val notices = messages.receiveAsFlow()
    fun notify(text: String, settings: Boolean = false) { messages.trySend(Notice(text, settings)) }
}

fun durationLabel(ms: Long): String {
    val seconds = ms / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}
