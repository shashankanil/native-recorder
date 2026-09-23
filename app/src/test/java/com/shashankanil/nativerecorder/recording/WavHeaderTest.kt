package com.shashankanil.nativerecorder.recording

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavHeaderTest {
    @Test fun oneSecondHasPlayablePcmFormatAndCorrectChunkSizes() {
        val header = wavHeader(88_200, 44_100)
        val fields = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(44, header.size)
        assertEquals("RIFF", String(header, 0, 4, Charsets.US_ASCII))
        assertEquals(88_236, fields.getInt(4))
        assertEquals("WAVE", String(header, 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(header, 12, 4, Charsets.US_ASCII))
        assertEquals(16, fields.getInt(16))
        assertEquals(1, fields.getShort(20).toInt())
        assertEquals(1, fields.getShort(22).toInt())
        assertEquals(44_100, fields.getInt(24))
        assertEquals(88_200, fields.getInt(28))
        assertEquals(2, fields.getShort(32).toInt())
        assertEquals(16, fields.getShort(34).toInt())
        assertEquals("data", String(header, 36, 4, Charsets.US_ASCII))
        assertEquals(88_200, fields.getInt(40))
    }
    @Test fun largeRecordingPreservesUnsignedRiffSizes() {
        val size = 3_000_000_000L
        val fields = ByteBuffer.wrap(wavHeader(size, 44_100)).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(size, fields.getInt(40).toLong() and 0xFFFFFFFFL)
        assertEquals(size + 36, fields.getInt(4).toLong() and 0xFFFFFFFFL)
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedRiff() { wavHeader(0x1_0000_0000L, 44_100) }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsPartialSample() { wavHeader(3, 44_100) }
}
