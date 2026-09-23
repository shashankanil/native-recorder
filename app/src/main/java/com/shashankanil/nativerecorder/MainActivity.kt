package com.shashankanil.nativerecorder

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.*
import com.shashankanil.nativerecorder.ui.home.HomeRoute
import com.shashankanil.nativerecorder.ui.home.RecorderViewModel
import com.shashankanil.nativerecorder.ui.theme.RecorderTheme

class MainActivity : ComponentActivity() {
    private val model: RecorderViewModel by viewModels()
    private var widgetRequest by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC
        widgetRequest = savedInstanceState == null && intent.action == WIDGET_RECORD
        setContent { RecorderTheme { HomeRoute(model, widgetRequest) { widgetRequest = false; intent.action = null } } }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        widgetRequest = intent.action == WIDGET_RECORD
    }
    override fun onStop() { model.pausePlayback(); super.onStop() }
    companion object { const val WIDGET_RECORD = "com.shashankanil.nativerecorder.WIDGET_RECORD" }
}
