package com.shashankanil.nativerecorder

import android.app.Application
import com.shashankanil.nativerecorder.storage.RecordingRepository
import com.shashankanil.nativerecorder.widget.RecorderWidget
import kotlinx.coroutines.*

class RecorderApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    override fun onCreate() {
        super.onCreate()
        // A killed process cannot resume MediaRecorder or reuse a projection token.
        RecordingRepository(this).discardIncomplete()
        applicationScope.launch { runCatching { RecorderWidget.refresh(this@RecorderApplication) } }
    }
}
