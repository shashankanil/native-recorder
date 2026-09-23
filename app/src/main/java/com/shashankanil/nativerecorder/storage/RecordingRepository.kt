package com.shashankanil.nativerecorder.storage

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.AtomicFile
import com.shashankanil.nativerecorder.recording.Source
import org.json.JSONObject
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.UUID

data class Recording(val file: File, val title: String, val durationMs: Long, val createdAt: Long, val source: Source)

class RecordingRepository(context: Context) {
    val directory = File(context.filesDir, "recordings").apply { mkdirs() }
    fun newFile(source: Source): File = File(directory,
        "${System.currentTimeMillis()}_${source.name}_${UUID.randomUUID()}.${if (source == Source.MIC) "m4a" else "wav"}.part")

    fun complete(part: File, source: Source, duration: Long): Recording {
        check(part.length() > if (source == Source.DEVICE) 44 else 0) { "No audio was captured. Try a longer recording." }
        val file = File(directory, part.name.removeSuffix(".part"))
        check(part.renameTo(file)) { "Could not save recording." }
        val created = file.name.substringBefore('_').toLongOrNull() ?: file.lastModified()
        val item = Recording(file, "${source.label} ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(created))}", duration, created, source)
        // The audio remains discoverable by scanning even if metadata cannot be written.
        runCatching { writeMetadata(item) }
        return item
    }

    fun list(): List<Recording> = directory.listFiles().orEmpty()
        .filter { it.extension == "m4a" || it.extension == "wav" }
        .map { file ->
            val metadata = runCatching { JSONObject(File(directory, "${file.name}.json").readText()) }.getOrNull()
            val created = file.name.substringBefore('_').toLongOrNull() ?: file.lastModified()
            val source = if (file.name.contains("_DEVICE_")) Source.DEVICE else Source.MIC
            Recording(file, metadata?.optString("title")?.takeIf { it.isNotBlank() }
                ?: "${source.label} ${DateFormat.getDateTimeInstance().format(Date(created))}",
                metadata?.optLong("duration", -1)?.takeIf { it >= 0 } ?: readDuration(file), created, source)
        }.sortedByDescending { it.createdAt }

    fun rename(item: Recording, title: String) { writeMetadata(item.copy(title = title.trim().take(120))) }
    fun delete(item: Recording) {
        check(item.file.delete() || !item.file.exists()) { "Could not delete recording." }
        File(directory, "${item.file.name}.json").delete()
    }
    fun discardIncomplete() { directory.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() } }

    private fun writeMetadata(item: Recording) {
        val atomic = AtomicFile(File(directory, "${item.file.name}.json"))
        val stream = atomic.startWrite()
        try {
            stream.write(JSONObject().put("title", item.title).put("duration", item.durationMs).toString().toByteArray())
            atomic.finishWrite(stream)
        } catch (error: Exception) { atomic.failWrite(stream); throw error }
    }
    private fun readDuration(file: File): Long = runCatching {
        val retriever = MediaMetadataRetriever()
        try { retriever.setDataSource(file.path); retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L }
        finally { retriever.release() }
    }.getOrDefault(0L)
}
