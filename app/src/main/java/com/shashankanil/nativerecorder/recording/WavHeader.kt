package com.shashankanil.nativerecorder.recording

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** RIFF/WAVE header for the device capture pipeline's mono, signed PCM16 stream. */
internal fun wavHeader(bytesWritten: Long, sampleRate: Int): ByteArray {
    require(bytesWritten in 0..(0xFFFFFFFFL - 36) && bytesWritten % 2L == 0L)
    require(sampleRate in 1..192_000)
    return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray(Charsets.US_ASCII))
        putInt((36 + bytesWritten).toInt())
        put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
        putInt(16).putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2)
        putShort(2).putShort(16)
        put("data".toByteArray(Charsets.US_ASCII)).putInt(bytesWritten.toInt())
    }.array()
}
