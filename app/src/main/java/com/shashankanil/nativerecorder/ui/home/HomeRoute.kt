package com.shashankanil.nativerecorder.ui.home

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import androidx.core.net.toUri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.shashankanil.nativerecorder.recording.*

@Composable
fun HomeRoute(model: RecorderViewModel, widgetRequest: Boolean, consumeWidgetRequest: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val session by model.session.collectAsStateWithLifecycle()
    val recordings by model.recordings.collectAsStateWithLifecycle()
    val playing by model.playing.collectAsStateWithLifecycle()
    val starting by model.starting.collectAsStateWithLifecycle()
    var source by rememberSaveable { mutableStateOf(Source.MIC) }
    var rationale by rememberSaveable { mutableStateOf<String?>(null) }
    var requesting by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val projection = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        requesting = false
        if (result.resultCode == Activity.RESULT_OK && result.data != null) model.start(Source.DEVICE, result.resultCode, result.data)
        else { source = Source.MIC; model.message("Device audio consent declined. Microphone mode is available.") }
    }
    val startCapture: () -> Unit = {
        if (source == Source.DEVICE && Build.VERSION.SDK_INT >= 29) {
            try { projection.launch(context.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent()) }
            catch (e: Exception) { requesting = false; model.message("Device audio unavailable: ${e.message}") }
        } else { requesting = false; model.start(Source.MIC) }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) model.message("Notifications denied. Enable them in Settings for recording controls.", settings = true)
        startCapture()
    }
    val ensureNotifications: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) rationale = "notifications"
        else startCapture()
    }
    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) ensureNotifications()
        else { requesting = false; model.message("Audio permission denied. Enable microphone access in Settings to record.", settings = true) }
    }
    val ensureAudio: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) rationale = "audio"
        else ensureNotifications()
    }
    val requestRecord: () -> Unit = {
        if (!requesting && !starting && session.phase == Phase.IDLE) {
            requesting = true
            model.stopPlayback()
            if (source == Source.DEVICE) rationale = "device" else ensureAudio()
        }
    }
    LaunchedEffect(widgetRequest) {
        if (widgetRequest) {
            consumeWidgetRequest()
            if (session.phase == Phase.IDLE && !requesting) {
                source = if (Build.VERSION.SDK_INT >= 29) Source.DEVICE else Source.MIC
                requestRecord()
            }
        }
    }
    LaunchedEffect(model, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            model.notices.collect { notice ->
                if (snackbar.showSnackbar(notice.text, actionLabel = if (notice.settings) "Settings" else null,
                        duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
                }
            }
        }
    }
    HomeScreen(session, recordings, playing, source, starting || requesting, snackbar, model.storagePath,
        onSource = { source = it }, onRecord = requestRecord, onCommand = model::command,
        onPlay = model::togglePlayback, onDelete = model::delete, onRename = model::rename)
    rationale?.let { kind ->
        AlertDialog(onDismissRequest = { rationale = null; requesting = false },
            title = { Text(when (kind) { "device" -> "About device audio"; "audio" -> "Allow audio recording"; else -> "Recording notifications" }) },
            text = { Text(when (kind) {
                "device" -> "Records media and games only when the other app allows capture. Meet, calls and VoIP audio are often not capturable and may produce silence. Use Mic to capture sound from a speaker instead; quality varies. Android will ask for screen/audio capture consent each time. This app saves audio only."
                "audio" -> "Android requires microphone permission for both microphone and device playback capture. Recordings stay in this app’s storage on your device."
                else -> "Allow notifications to see recording status and pause or stop while the app is in the background. Recording can continue if you decline."
            }) },
            confirmButton = { TextButton(onClick = {
                rationale = null
                when (kind) { "device" -> ensureAudio(); "audio" -> audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                    else -> if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else startCapture() }
            }) { Text("Continue") } },
            dismissButton = { TextButton(onClick = {
                rationale = null
                if (kind == "device") { source = Source.MIC; ensureAudio() }
                else { requesting = false }
            }) { Text(if (kind == "device") "Use Mic" else "Cancel") } })
    }
}
