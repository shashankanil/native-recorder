package com.shashankanil.nativerecorder.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.shashankanil.nativerecorder.recording.Phase
import com.shashankanil.nativerecorder.recording.RecordingState
import com.shashankanil.nativerecorder.recording.durationLabel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Nothing-inspired monochrome squircle artwork for the home widget.
 * No bundled typefaces or third-party brand assets — just geometric bars + sans text.
 */
internal object WidgetArtwork {
    private const val Surface = 0xFF121212
    private const val White = 0xFFFFFFFF
    private const val Grey = 0xFF8A8A8A
    private const val Dim = 0xFF3A3A3A
    private const val Red = 0xFFE53935

    fun render(kind: WidgetPages.Kind, state: RecordingState, size: Int = 420): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        c.scale(size / 200f, size / 200f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Surface.toInt()
        c.drawRoundRect(0f, 0f, 200f, 200f, 36f, 36f, p)

        when (kind) {
            WidgetPages.Kind.WAVEFORM -> drawWaveform(c, p, state)
            WidgetPages.Kind.TIMER -> drawTimer(c, p, state)
            WidgetPages.Kind.ACTION -> drawAction(c, p, state)
        }
        return bitmap
    }

    private fun drawWaveform(c: Canvas, p: Paint, state: RecordingState) {
        val active = state.phase == Phase.RECORDING || state.phase == Phase.PAUSED
        val saving = state.phase == Phase.SAVING
        val phase = if (state.phase == Phase.RECORDING) state.elapsedMs / 180.0 else 0.0
        drawBars(c, p, phase, accent = when {
            state.phase == Phase.RECORDING -> Red
            state.phase == Phase.PAUSED -> Grey
            saving -> Dim
            else -> White
        }, energy = when {
            state.phase == Phase.RECORDING -> 1f
            state.phase == Phase.PAUSED -> 0.45f
            saving -> 0.25f
            else -> 0.7f
        })

        if (active || saving) {
            // Status-pill energy: red capsule with live MM:SS
            val label = when (state.phase) {
                Phase.PAUSED -> "PAUSED  ${durationLabel(state.elapsedMs)}"
                Phase.SAVING -> "SAVING"
                else -> durationLabel(state.elapsedMs)
            }
            drawPill(c, p, label, fill = if (state.phase == Phase.RECORDING) Red else Dim)
        } else {
            // Quiet corner glyph — mic-dot
            p.color = Grey.toInt()
            c.drawCircle(168f, 168f, 4.5f, p)
        }
    }

    private fun drawTimer(c: Canvas, p: Paint, state: RecordingState) {
        val idle = state.phase == Phase.IDLE
        p.color = (if (idle) Grey else White).toInt()
        p.textSize = 11f
        p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        p.textAlign = Paint.Align.LEFT
        c.drawText(if (idle) "DEVICE" else state.source.label.uppercase(), 18f, 32f, p)

        p.color = (when (state.phase) {
            Phase.RECORDING -> Red
            Phase.PAUSED -> Grey
            Phase.SAVING -> Dim
            else -> White
        }).toInt()
        p.textSize = if (idle) 22f else 34f
        p.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        p.textAlign = Paint.Align.CENTER
        val main = if (idle) "Ready" else durationLabel(state.elapsedMs)
        c.drawText(main, 100f, 112f, p)

        p.color = Grey.toInt()
        p.textSize = 10f
        p.textAlign = Paint.Align.CENTER
        val sub = when (state.phase) {
            Phase.IDLE -> "Tap to record"
            Phase.RECORDING -> "Recording"
            Phase.PAUSED -> "Paused"
            Phase.SAVING -> "Saving…"
        }
        c.drawText(sub, 100f, 138f, p)

        // Decorative thin bars under the timer
        drawBars(c, p, phase = state.elapsedMs / 220.0, accent = Dim, energy = 0.35f, cy = 168f, maxH = 18f, barCount = 9)
    }

    private fun drawAction(c: Canvas, p: Paint, state: RecordingState) {
        when (state.phase) {
            Phase.IDLE -> {
                p.color = White.toInt()
                p.textSize = 14f
                p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                p.textAlign = Paint.Align.CENTER
                c.drawText("Record", 100f, 88f, p)
                p.color = Grey.toInt()
                p.textSize = 10f
                c.drawText("Device audio", 100f, 108f, p)
                p.color = White.toInt()
                c.drawCircle(100f, 148f, 16f, p)
                p.color = Surface.toInt()
                c.drawCircle(100f, 148f, 7f, p)
            }
            Phase.SAVING -> {
                p.color = Grey.toInt()
                p.textSize = 14f
                p.textAlign = Paint.Align.CENTER
                c.drawText("Saving…", 100f, 108f, p)
            }
            else -> {
                p.color = Red.toInt()
                p.textSize = 16f
                p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                p.textAlign = Paint.Align.CENTER
                c.drawText("Stop?", 100f, 72f, p)
                p.color = Grey.toInt()
                p.textSize = 10f
                c.drawText(durationLabel(state.elapsedMs), 100f, 92f, p)

                // Confirm capsule
                p.color = Red.toInt()
                c.drawRoundRect(RectF(36f, 118f, 164f, 156f), 20f, 20f, p)
                p.color = White.toInt()
                p.textSize = 12f
                c.drawText("Tap to stop", 100f, 142f, p)
            }
        }
    }

    private fun drawPill(c: Canvas, p: Paint, text: String, fill: Long) {
        p.textSize = 11f
        p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        p.textAlign = Paint.Align.LEFT
        val padX = 10f
        val w = p.measureText(text) + padX * 2 + 14f
        val left = 14f
        val top = 14f
        val bottom = 36f
        p.color = fill.toInt()
        c.drawRoundRect(RectF(left, top, left + w, bottom), 11f, 11f, p)
        // mic dot
        p.color = White.toInt()
        c.drawCircle(left + 11f, (top + bottom) / 2f, 3.2f, p)
        c.drawText(text, left + 18f, bottom - 7f, p)
    }

    private fun drawBars(
        c: Canvas,
        p: Paint,
        phase: Double,
        accent: Long,
        energy: Float,
        cy: Float = 104f,
        maxH: Float = 58f,
        barCount: Int = 11,
    ) {
        p.color = accent.toInt()
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 5.5f
        p.style = Paint.Style.STROKE
        val span = 132f
        val startX = (200f - span) / 2f
        val mid = (barCount - 1) / 2f
        for (i in 0 until barCount) {
            val t = if (mid == 0f) 0f else (i - mid) / mid
            val envelope = (cos(t * PI / 2.0)).toFloat().coerceIn(0.18f, 1f)
            val wobble = if (energy > 0.5f) {
                (0.55f + 0.45f * sin(phase + i * 0.55).toFloat())
            } else {
                0.85f + 0.15f * sin(i * 0.9).toFloat()
            }
            val h = maxH * envelope * energy * wobble
            val x = startX + i * (span / (barCount - 1).coerceAtLeast(1))
            c.drawLine(x, cy - h, x, cy + h, p)
        }
        p.style = Paint.Style.FILL
    }
}
