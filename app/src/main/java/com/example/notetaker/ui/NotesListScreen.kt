package com.example.notetaker.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.Alignment
import com.example.notetaker.data.Note
import com.example.notetaker.data.Template
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatNoteDate(millis: Long): String =
    SimpleDateFormat("EEE, MMM d yyyy · h:mm a", Locale.getDefault()).format(Date(millis))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
    onOpenNote: (Long) -> Unit,
    onNewNote: (templateId: Long) -> Unit,
    onSettings: () -> Unit,
    viewModel: NotesListViewModel = viewModel()
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Note?>(null) }
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    var choosingTemplate by remember { mutableStateOf(false) }
    var pendingDeleteTemplate by remember { mutableStateOf<Template?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Note Taker") },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                // With no templates there is nothing to choose, so go straight to a blank note.
                if (templates.isEmpty()) onNewNote(0L) else choosingTemplate = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "New note")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search notes") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true
            )
            if (notes.isEmpty()) {
                Text(
                    if (query.isBlank()) "No notes yet. Tap + to add one." else "No matching notes.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notes, key = { it.id }) { note ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value != SwipeToDismissBoxValue.Settled) pendingDelete = note
                            false // snap back; the note is removed only after confirmation
                        }
                    )
                    SwipeToDismissBox(state = dismissState, backgroundContent = {}) {
                        NoteCard(
                            note = note,
                            onClick = { onOpenNote(note.id) },
                            onLongClick = { pendingDelete = note }
                        )
                    }
                }
            }
        }
    }

    if (choosingTemplate) {
        AlertDialog(
            onDismissRequest = { choosingTemplate = false },
            title = { Text("New note") },
            text = {
                LazyColumn {
                    item {
                        TextButton(
                            onClick = { choosingTemplate = false; onNewNote(0L) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Blank note", Modifier.fillMaxWidth()) }
                    }
                    items(templates, key = { it.id }) { t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { choosingTemplate = false; onNewNote(t.id) },
                                modifier = Modifier.weight(1f)
                            ) { Text(t.name, Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            IconButton(onClick = { pendingDeleteTemplate = t }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete template")
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosingTemplate = false }) { Text("Cancel") } }
        )
    }

    pendingDeleteTemplate?.let { t ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTemplate = null },
            title = { Text("Delete template?") },
            text = { Text("\"${t.name}\" will be deleted. Notes made from it are not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTemplate(t.id)
                    pendingDeleteTemplate = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteTemplate = null }) { Text("Cancel") } }
        )
    }

    pendingDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete note?") },
            text = { Text("This note will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(note.id)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(note: Note, onClick: () -> Unit, onLongClick: () -> Unit) {
    val lines = note.content.lines().filter { it.isNotBlank() }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                formatNoteDate(note.createdAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            if (note.title.isNotBlank()) {
                Text(
                    note.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                lines.firstOrNull() ?: "(empty)",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = if (note.title.isNotBlank()) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (lines.size == 1) "1 line" else "${lines.size} lines",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
