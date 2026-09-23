package com.shashankanil.nativerecorder.ui.home

import android.app.Application
import android.content.Intent
import android.media.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shashankanil.nativerecorder.recording.*
import com.shashankanil.nativerecorder.service.RecordingForegroundService as Service
import com.shashankanil.nativerecorder.storage.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class RecorderViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application
    private val repository = RecordingRepository(application)
    val session = RecordingSession.state.asStateFlow()
    val notices = RecordingSession.notices
    private val _recordings = MutableStateFlow<List<Recording>>(emptyList())
    val recordings = _recordings.asStateFlow()
    private val _playing = MutableStateFlow<String?>(null)
    val playing = _playing.asStateFlow()
    private val _starting = MutableStateFlow(false)
    val starting = _starting.asStateFlow()
    val storagePath: String get() = repository.directory.path
    private var player: MediaPlayer? = null
    private var playerFile: String? = null
    private val audioManager = application.getSystemService(AudioManager::class.java)
    private val audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(audioAttributes).setOnAudioFocusChangeListener { change ->
            if (change < 0) pausePlayback()
        }.build()
    init {
        viewModelScope.launch { RecordingSession.libraryVersion.collect { refresh() } }
        viewModelScope.launch { session.collect { if (it.phase != Phase.IDLE) { _starting.value = false; stopPlayback() } } }
    }
    private suspend fun refresh() {
        runCatching { withContext(Dispatchers.IO) { repository.list() } }
            .onSuccess { _recordings.value = it }.onFailure { message("Could not read recordings: ${it.message}") }
    }
    fun start(source: Source, code: Int = 0, data: Intent? = null) {
        if (session.value.phase != Phase.IDLE || _starting.value) return
        stopPlayback(); _starting.value = true
        try {
            ContextCompat.startForegroundService(context, Intent(context, Service::class.java).setAction(Service.START)
                .putExtra(Service.SOURCE, source.name).putExtra(Service.RESULT_CODE, code).putExtra(Service.PROJECTION_DATA, data))
        } catch (e: Exception) { _starting.value = false; message("Could not start recording: ${e.message}") }
        viewModelScope.launch { delay(3000); _starting.value = false }
    }
    fun command(action: String) {
        if (session.value.phase == Phase.IDLE) return
        runCatching { context.startService(Intent(context, Service::class.java).setAction(action)) }
            .onFailure { message("Recording control failed: ${it.message}") }
    }
    fun delete(item: Recording) {
        if (playerFile == item.file.path) stopPlayback()
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.delete(item) } }
                .onSuccess { refresh(); message("Recording deleted") }.onFailure { message(it.message ?: "Could not delete recording") }
        }
    }
    fun rename(item: Recording, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.rename(item, title) } }
                .onSuccess { refresh() }.onFailure { message("Could not rename recording: ${it.message}") }
        }
    }
    fun togglePlayback(item: Recording) {
        if (session.value.phase != Phase.IDLE || _starting.value) return
        if (_playing.value == item.file.path) { pausePlayback(); return }
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            message("Audio playback is unavailable right now."); return
        }
        try {
            if (playerFile == item.file.path && player != null) {
                player!!.start(); _playing.value = item.file.path
            } else {
                releasePlayer()
                playerFile = item.file.path
                player = MediaPlayer().apply {
                    setAudioAttributes(audioAttributes)
                    setDataSource(item.file.path)
                    setOnPreparedListener { it.start(); _playing.value = item.file.path }
                    setOnCompletionListener { stopPlayback() }
                    setOnErrorListener { _, _, _ -> stopPlayback(); message("Could not play this recording."); true }
                    prepareAsync()
                }
            }
        } catch (e: Exception) { stopPlayback(); message("Could not play recording: ${e.message}") }
    }
    fun pausePlayback() {
        // Release a still-preparing player so it cannot start after the Activity leaves.
        if (_playing.value == null) releasePlayer() else runCatching { player?.pause() }
        _playing.value = null
        audioManager.abandonAudioFocusRequest(focusRequest)
    }
    private fun releasePlayer() { player?.release(); player = null; playerFile = null; _playing.value = null }
    fun stopPlayback() { releasePlayer(); audioManager.abandonAudioFocusRequest(focusRequest) }
    fun message(text: String, settings: Boolean = false) = RecordingSession.notify(text, settings)
    override fun onCleared() { stopPlayback(); super.onCleared() }
}
