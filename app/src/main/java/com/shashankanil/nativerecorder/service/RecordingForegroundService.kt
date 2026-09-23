package com.shashankanil.nativerecorder.service

import android.app.*
import android.annotation.SuppressLint
import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.shashankanil.nativerecorder.MainActivity
import com.shashankanil.nativerecorder.R
import com.shashankanil.nativerecorder.RecorderApplication
import com.shashankanil.nativerecorder.recording.*
import com.shashankanil.nativerecorder.storage.RecordingRepository
import com.shashankanil.nativerecorder.widget.RecorderWidget
import kotlinx.coroutines.*
import java.io.File

class RecordingForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository by lazy { RecordingRepository(this) }
    private var capture: AudioCapture? = null
    private var projection: MediaProjection? = null
    private var part: File? = null
    private var ticker: Job? = null
    private var accumulated = 0L
    private var resumedAt = 0L
    private var wakeLock: PowerManager.WakeLock? = null
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() { finish("Screen/audio capture permission ended.") }
    }
    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Recording", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            START -> if (RecordingSession.state.value.phase == Phase.IDLE) start(intent)
            PAUSE -> pause()
            RESUME -> resume()
            STOP -> { if (capture == null) stopSelf() else finish() }
            else -> if (capture == null) stopSelf()
        }
        return START_NOT_STICKY
    }
    // User-controlled foreground sessions have no time limit; cleanup() releases the lock.
    @SuppressLint("WakelockTimeout")
    @Suppress("DEPRECATION")
    private fun start(intent: Intent) {
        val source = if (intent.getStringExtra(SOURCE) == Source.DEVICE.name) Source.DEVICE else Source.MIC
        try {
            val type = when {
                source == Source.DEVICE && Build.VERSION.SDK_INT >= 29 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                source == Source.MIC && Build.VERSION.SDK_INT >= 30 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                else -> 0
            }
            RecordingSession.state.value = RecordingState(Phase.RECORDING, source)
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), if (Build.VERSION.SDK_INT >= 29) type else 0)
            part = repository.newFile(source)
            capture = if (source == Source.MIC) MicCapture(this, part!!) { reason -> scope.launch { finish(reason) } } else {
                if (Build.VERSION.SDK_INT < 29) throw IllegalStateException("Device audio requires Android 10 or newer.")
                val data = intent.getParcelableExtra<Intent>(PROJECTION_DATA) ?: error("Capture consent is missing.")
                val token = getSystemService(MediaProjectionManager::class.java)
                    .getMediaProjection(intent.getIntExtra(RESULT_CODE, Activity.RESULT_CANCELED), data)
                    ?: error("Capture consent expired. Please try again.")
                projection = token
                token.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
                DeviceCapture(token, part!!) { reason -> scope.launch { finish(reason) } }
            }
            capture!!.start()
            accumulated = 0L
            resumedAt = SystemClock.elapsedRealtime()
            wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:recording").apply { acquire() }
            refreshWidget()
            ticker = scope.launch {
                while (isActive) {
                    delay(500)
                    if (RecordingSession.state.value.phase == Phase.RECORDING) {
                        RecordingSession.state.value = RecordingSession.state.value.copy(elapsedMs = elapsed())
                        updateNotification()
                    }
                }
            }
        } catch (e: Exception) {
            cleanup()
            part?.delete(); part = null
            RecordingSession.state.value = RecordingState()
            RecordingSession.notify("Could not start recording: ${e.message ?: "audio unavailable"}")
            refreshWidget(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
        }
    }
    private fun elapsed() = accumulated + if (RecordingSession.state.value.phase == Phase.RECORDING) SystemClock.elapsedRealtime() - resumedAt else 0
    private fun pause() {
        if (RecordingSession.state.value.phase != Phase.RECORDING) return
        try {
            capture?.pause(); accumulated = elapsed()
            RecordingSession.state.value = RecordingSession.state.value.copy(phase = Phase.PAUSED, elapsedMs = accumulated)
            updateSurfaces()
        } catch (e: Exception) { finish("Could not pause: ${e.message}") }
    }
    private fun resume() {
        if (RecordingSession.state.value.phase != Phase.PAUSED) return
        try {
            capture?.resume(); resumedAt = SystemClock.elapsedRealtime()
            RecordingSession.state.value = RecordingSession.state.value.copy(phase = Phase.RECORDING)
            updateSurfaces()
        } catch (e: Exception) { finish("Could not resume: ${e.message}") }
    }
    private fun finish(reason: String? = null) {
        val engine = capture ?: return
        val state = RecordingSession.state.value
        if (state.phase == Phase.SAVING) return
        val duration = elapsed()
        ticker?.cancel()
        RecordingSession.state.value = state.copy(phase = Phase.SAVING, elapsedMs = duration)
        updateSurfaces()
        scope.launch {
            try {
                val saved = withContext(Dispatchers.IO) {
                    engine.finish()
                    repository.complete(requireNotNull(part), state.source, duration)
                }
                part = null
                RecordingSession.libraryVersion.value++
                RecordingSession.notify(listOfNotNull(reason, "Saved ${saved.title}").joinToString(" "))
            } catch (e: Exception) {
                part?.delete(); part = null
                RecordingSession.notify("${reason?.plus(" ") ?: ""}Could not save recording: ${e.message}")
            } finally {
                cleanup()
                RecordingSession.state.value = RecordingState()
                refreshWidget()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }
    private fun cleanup() {
        ticker?.cancel(); ticker = null
        runCatching { capture?.release() }; capture = null
        projection?.unregisterCallback(projectionCallback)
        runCatching { projection?.stop() }; projection = null
        wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
    }
    private fun updateNotification() {
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
        }
    }
    private fun updateSurfaces() {
        updateNotification()
        refreshWidget()
    }
    private fun refreshWidget() {
        (application as RecorderApplication).applicationScope.launch { runCatching { RecorderWidget.refresh(this@RecordingForegroundService) } }
    }
    private fun notification(): Notification {
        val state = RecordingSession.state.value
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("${state.source.label} • ${state.phase.name.lowercase().replaceFirstChar { it.uppercase() }}")
            .setContentText(durationLabel(state.elapsedMs)).setContentIntent(open).setOngoing(true)
            .setOnlyAlertOnce(true).setCategory(NotificationCompat.CATEGORY_SERVICE)
        if (state.phase != Phase.SAVING) {
            val paused = state.phase == Phase.PAUSED
            builder.addAction(0, if (paused) "Resume" else "Pause", commandPending(if (paused) RESUME else PAUSE, 1))
            builder.addAction(0, "Stop", commandPending(STOP, 2))
        }
        return builder.build()
    }
    private fun commandPending(action: String, code: Int) = PendingIntent.getService(this, code,
        Intent(this, RecordingForegroundService::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    override fun onDestroy() {
        cleanup()
        part?.delete()
        RecordingSession.state.value = RecordingState()
        refreshWidget(); scope.cancel()
        super.onDestroy()
    }
    companion object {
        const val START = "recorder.START"
        const val PAUSE = "recorder.PAUSE"
        const val RESUME = "recorder.RESUME"
        const val STOP = "recorder.STOP"
        const val SOURCE = "source"
        const val PROJECTION_DATA = "projection_data"
        const val RESULT_CODE = "result_code"
        private const val CHANNEL = "recording"
        private const val NOTIFICATION_ID = 1
    }
}
