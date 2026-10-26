package com.example.soundengitoolkit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun defaultProjectName(): String =
    "Recording " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

@Composable
fun SaveDialog(defaultName: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(defaultName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save recording") },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ProjectsPanel(projects: List<ProjectEntity>, onLoad: (ProjectEntity) -> Unit, onDelete: (ProjectEntity) -> Unit) {
    var toDelete by remember { mutableStateOf<ProjectEntity?>(null) }
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Saved recordings (${projects.size})", style = MaterialTheme.typography.titleSmall)
        if (projects.isEmpty()) Text("Nothing saved yet", style = MaterialTheme.typography.bodySmall, color = Color(0xFFAAAAAA))
        projects.forEach { p ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.bodyMedium)
                    Text("%s, %.1f s, %.0f BPM".format(fmt.format(Date(p.createdAt)), p.durationSamples / SR, p.bpm),
                        style = MaterialTheme.typography.labelSmall, color = Color(0xFFAAAAAA))
                }
                TextButton(onClick = { onLoad(p) }) { Text("Load") }
                TextButton(onClick = { toDelete = p }) { Text("Delete") }
            }
        }
    }
    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete \"${p.name}\"?") },
            text = { Text("The file and its settings will be removed from this device.") },
            confirmButton = { TextButton(onClick = { onDelete(p); toDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun HistoryPanel(entries: List<AiEntryEntity>, onPick: (String) -> Unit, onClear: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { expanded = !expanded }) {
                Text("AI history (${entries.size}) " + if (expanded) "▲" else "▼")
            }
            if (expanded && entries.isNotEmpty()) TextButton(onClick = onClear) { Text("Clear") }
        }
        if (expanded) entries.take(15).forEach { en ->
            Column(Modifier.fillMaxWidth().clickable { onPick(en.prompt) }.padding(vertical = 4.dp)) {
                Text((if (en.ok) "✓ " else "✗ ") + en.prompt, style = MaterialTheme.typography.bodySmall)
                Text("${fmt.format(Date(en.createdAt))} · ${en.source}  (tap to reuse)",
                    style = MaterialTheme.typography.labelSmall, color = Color(0xFFAAAAAA))
            }
        }
    }
}
