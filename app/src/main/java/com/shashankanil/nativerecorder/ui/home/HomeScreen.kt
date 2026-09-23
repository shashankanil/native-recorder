package com.shashankanil.nativerecorder.ui.home

import android.os.Build
import android.text.format.DateUtils
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shashankanil.nativerecorder.recording.*
import com.shashankanil.nativerecorder.service.RecordingForegroundService as Service
import com.shashankanil.nativerecorder.storage.Recording

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    session: RecordingState, recordings: List<Recording>, playing: String?, source: Source,
    busy: Boolean, snackbar: SnackbarHostState, storagePath: String,
    onSource: (Source) -> Unit, onRecord: () -> Unit, onCommand: (String) -> Unit,
    onPlay: (Recording) -> Unit, onDelete: (Recording) -> Unit, onRename: (Recording, String) -> Unit,
) {
    var settings by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Recording?>(null) }
    var renaming by remember { mutableStateOf<Recording?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    val idle = session.phase == Phase.IDLE
    Scaffold(
        topBar = { TopAppBar(title = { Text("Recorder") }, actions = {
            IconButton(onClick = { settings = true }) { Icon(Icons.Default.Settings, "Settings") }
        }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (idle && !busy) ExtendedFloatingActionButton(onClick = onRecord,
                icon = { Icon(Icons.Default.Mic, "Start recording") }, text = { Text("Record") })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Source.entries.forEach { option ->
                    FilterChip(selected = source == option, onClick = { onSource(option) }, label = { Text(option.label) },
                        enabled = idle && !busy && (option != Source.DEVICE || Build.VERSION.SDK_INT >= 29))
                }
            }
            if (Build.VERSION.SDK_INT < 29) Text("Device audio requires Android 10 or newer. Mic is available.",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
            Text("Audio stays on this device.", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
            if (!idle) Card(Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${session.source.label} • ${session.phase.name.lowercase().replaceFirstChar { it.uppercase() }}")
                    Text(durationLabel(session.elapsedMs), style = MaterialTheme.typography.displaySmall)
                    if (session.phase == Phase.SAVING) CircularProgressIndicator(Modifier.padding(12.dp))
                    else Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        val paused = session.phase == Phase.PAUSED
                        FilledTonalIconButton(onClick = { onCommand(if (paused) Service.RESUME else Service.PAUSE) }) {
                            Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, if (paused) "Resume recording" else "Pause recording")
                        }
                        FilledIconButton(onClick = { onCommand(Service.STOP) },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) {
                            Icon(Icons.Default.Stop, "Stop recording")
                        }
                    }
                }
            }
            if (busy && idle) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            if (recordings.isEmpty()) Column(Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.MicNone, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Text("Tap record to capture audio on this device", textAlign = TextAlign.Center)
            } else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 96.dp)) {
                items(recordings, key = { it.file.name }) { item ->
                    ListItem(
                        headlineContent = { Text(item.title, Modifier.clickable { renaming = item; title = item.title }
                            .semantics { contentDescription = "Rename ${item.title}" }) },
                        supportingContent = {
                            Column {
                                Text("${durationLabel(item.durationMs)} • ${DateUtils.getRelativeTimeSpanString(item.createdAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)}")
                                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                                    Text(item.source.label, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { onPlay(item) }, enabled = idle && !busy) {
                                    Icon(if (playing == item.file.path) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        if (playing == item.file.path) "Pause ${item.title}" else "Play ${item.title}")
                                }
                                IconButton(onClick = { deleting = item }) { Icon(Icons.Default.Delete, "Delete ${item.title}") }
                            }
                        })
                    HorizontalDivider()
                }
            }
        }
    }
    deleting?.let { item ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete recording?") },
            text = { Text("Delete “${item.title}” from this device? This cannot be undone.") },
            confirmButton = { TextButton(onClick = { onDelete(item); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
    }
    renaming?.let { item ->
        AlertDialog(onDismissRequest = { renaming = null }, title = { Text("Rename recording") },
            text = { OutlinedTextField(value = title, onValueChange = { title = it.take(120) }, label = { Text("Title") }, singleLine = true) },
            confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { onRename(item, title); renaming = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } })
    }
    if (settings) AlertDialog(onDismissRequest = { settings = false }, title = { Text("Settings") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Theme follows your system. Android 12+ uses wallpaper colors.")
            Text("Audio stays on this device. No account, network access or cloud backup. Uninstalling clears recordings.")
            Text("Storage: $storagePath")
            Text("Mic: AAC/M4A. Device: PCM/WAV (about 5 MB/min). Tap a recording title to rename it.")
            Text("Device capture may be silent when an app blocks capture. Meet and VoIP calls are often not capturable. Mic can capture sound from a speaker; quality varies.")
        } }, confirmButton = { TextButton(onClick = { settings = false }) { Text("Done") } })
}
