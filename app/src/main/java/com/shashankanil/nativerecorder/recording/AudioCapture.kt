package com.shashankanil.nativerecorder.recording

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.media.projection.MediaProjection
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean

interface AudioCapture {
    fun start()
    fun pause()
    fun resume()
    fun finish()
    fun release()
}

@Suppress("DEPRECATION")
class MicCapture(context: Context, file: File, onError: (String) -> Unit) : AudioCapture {
    private val recorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
    init {
        try {
            recorder.setOnErrorListener { _, what, extra -> onError("Microphone recording ended (code $what/$extra).") }
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(128_000)
            recorder.setAudioSamplingRate(44_100)
            recorder.setOutputFile(file.path)
            recorder.prepare()
        } catch (e: Exception) { recorder.release(); throw e }
    }
    override fun start() = recorder.start()
    override fun pause() = recorder.pause()
    override fun resume() = recorder.resume()
    override fun finish() = recorder.stop()
    override fun release() = recorder.release()
}

/** Mono PCM16 WAV. Paused buffers are drained but not written; no silence is inserted. */
@RequiresApi(29)
@SuppressLint("MissingPermission")
class DeviceCapture(projection: MediaProjection, private val file: File, private val onError: (String) -> Unit) : AudioCapture {
    private val sampleRate = 44_100
    private val bufferSize = maxOf(8192, AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2)
    private val recorder = AudioRecord.Builder()
        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
        .setBufferSizeInBytes(bufferSize)
        .setAudioPlaybackCaptureConfig(AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).build())
        .build()
    private val running = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private var writer: Thread? = null
    @Volatile private var failure: Exception? = null
    private var bytesWritten = 0L

    override fun start() {
        check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Device audio is unavailable." }
        recorder.startRecording()
        check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Could not start device audio." }
        running.set(true)
        writer = Thread({
            try {
                RandomAccessFile(file, "rw").use { out ->
                    out.setLength(0); out.write(ByteArray(44))
                    val buffer = ByteArray(bufferSize)
                    while (running.get()) {
                        val count = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                        if (!running.get()) break
                        check(count >= 0) { "Audio capture stopped (code $count)." }
                        if (!paused.get() && count > 0) {
                            check(bytesWritten + count <= 0xFFFFFFFFL - 36) { "WAV size limit reached. Start a new recording." }
                            out.write(buffer, 0, count); bytesWritten += count
                        }
                    }
                }
            } catch (e: Exception) { failure = e; onError(e.message ?: "Device audio failed.") }
        }, "DeviceAudioWriter").apply { start() }
    }
    override fun pause() { paused.set(true) }
    override fun resume() { paused.set(false) }
    override fun finish() {
        running.set(false)
        runCatching { recorder.stop() }
        writer?.join()
        // Finalize the valid prefix even if a read failed or the system revoked projection.
        RandomAccessFile(file, "rw").use { out ->
            out.seek(0); out.write(wavHeader(bytesWritten, sampleRate))
        }
        if (bytesWritten == 0L) throw failure ?: IllegalStateException("No audio was captured.")
    }
    override fun release() {
        running.set(false)
        runCatching { recorder.stop() }
        writer?.join()
        recorder.release()
    }
}
