package com.example.notetaker.ui

import android.app.Application
import androidx.activity.compose.BackHandler
import android.content.Intent
import android.util.Log
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.example.notetaker.data.InkThickness
import com.example.notetaker.data.SettingsRepository
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

@Composable
private fun PadButton(label: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = modifier.height(56.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

/** Acts on press, then repeats while held (after a short initial delay). */
@Composable
private fun RepeatPadButton(label: String, modifier: Modifier, onAction: () -> Unit) {
    val action by rememberUpdatedState(onAction)
    Surface(
        modifier = modifier.height(56.dp).pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown().consume()
                action()
                var wait = 400L
                while (true) {
                    val event = withTimeoutOrNull(wait) { awaitPointerEvent() }
                    if (event == null) {
                        action()
                        wait = 60L
                    } else if (event.changes.none { it.pressed }) {
                        break
                    }
                }
            }
        },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteEditScreen(noteId: Long, templateId: Long = 0L, onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val viewModel: NoteEditViewModel = viewModel(
        key = "note-$noteId-$templateId",
        factory = viewModelFactory { initializer { NoteEditViewModel(app, noteId, templateId) } }
    )
    var menuOpen by remember { mutableStateOf(false) }
    var templateName by remember { mutableStateOf<String?>(null) }
    val noteTitle by viewModel.title.collectAsStateWithLifecycle()
    var renameText by remember { mutableStateOf<String?>(null) }
    val value by viewModel.value.collectAsStateWithLifecycle()
    val note by viewModel.note.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val ink = remember { InkState() }
    val keyboard = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.isImeVisible
    val settings = remember { SettingsRepository(app) }
    val commitDelaySec by settings.commitDelaySec.collectAsStateWithLifecycle(SettingsRepository.DEFAULT_COMMIT_DELAY_SEC)
    val thickness by settings.inkThickness.collectAsStateWithLifecycle(InkThickness.Medium)
    val hw: HandwritingViewModel = viewModel()
    val modelState by hw.state.collectAsStateWithLifecycle()
    // Alternatives for the last commit, which one is in the text, and where it sits.
    var candidates by remember { mutableStateOf(emptyList<String>()) }
    var selected by remember { mutableIntStateOf(0) }
    var inserted by remember { mutableStateOf<InsertedRange?>(null) }

    // Layout of the notepad, used to move the cursor by visual row.
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    /** Moves the cursor to the end of the next row, without inserting anything. */
    fun cursorToNextRowEnd() {
        val v = viewModel.value.value
        val layout = textLayout
        val target = if (layout != null && layout.layoutInput.text.text == v.text) {
            val row = layout.getLineForOffset(v.selection.max.coerceAtMost(v.text.length))
            if (row + 1 < layout.lineCount) layout.getLineEnd(row + 1, visibleEnd = true) else v.text.length
        } else v.text.length
        viewModel.setValue(v.copy(selection = TextRange(target)))
    }

    fun dismissAlternatives() {
        candidates = emptyList()
        inserted = null
    }

    /** Puts [word] at [start] (replacing up to [oldEnd]) with auto-spacing; returns the new range. */
    fun place(start: Int, oldEnd: Int, word: String) {
        val v = viewModel.value.value
        val piece = withAutoSpace(v.text.substring(0, start), word.trim())
        val text = v.text.replaceRange(start, oldEnd, piece)
        viewModel.setValue(TextFieldValue(text, TextRange(start + piece.length)))
        inserted = InsertedRange(start, start + piece.length)
    }

    // Recognize once no stroke has started for COMMIT_DELAY_MS. Strokes written while
    // recognition runs are kept and picked up by the next round (activity changes again).
    val commitLock = remember { Mutex() }
    val scope = rememberCoroutineScope()

    /** Recognizes what is on the pad and inserts it. Serialized so the timer and buttons can't overlap. */
    suspend fun commitInk() = commitLock.withLock {
        if (modelState != ModelState.Ready || ink.isEmpty) return
        val count = ink.strokes.size
        val mlInk = ink.toMlKitInk(count)
        val v = viewModel.value.value
        val context = v.text.substring(0, v.selection.min).takeLast(20)
        val result = hw.recognize(mlInk, context, ink.width, ink.height)
        Log.d("Handwriting", "Recognized $count stroke(s): $result")
        val best = result.firstOrNull()?.trim().orEmpty()
        if (best.isEmpty()) {
            dismissAlternatives()
        } else {
            val cur = viewModel.value.value
            place(cur.selection.min, cur.selection.max, best)
            candidates = result.map { it.trim() }.filter { it.isNotEmpty() }.take(4)
            selected = 0
        }
        ink.removeFirst(count)
    }

    LaunchedEffect(ink.activity, modelState) {
        if (modelState != ModelState.Ready || ink.isDrawing || ink.isEmpty) return@LaunchedEffect
        delay((commitDelaySec * 1000).toLong())
        withContext(NonCancellable) { commitInk() }
    }

    BackHandler { viewModel.save(onClose) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        noteTitle.ifBlank { if (noteId == 0L) "New note" else "Note" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.save(onClose) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.save(onClose) }) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                    if (noteId != 0L) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Share") },
                            enabled = value.text.isNotBlank(),
                            onClick = {
                                menuOpen = false
                                val body = if (noteTitle.isBlank()) value.text else "$noteTitle\n\n${value.text}"
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    if (noteTitle.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, noteTitle)
                                    putExtra(Intent.EXTRA_TEXT, body)
                                }
                                context.startActivity(Intent.createChooser(send, "Share note"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                menuOpen = false
                                renameText = noteTitle
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Save as template") },
                            enabled = value.text.isNotBlank(),
                            onClick = {
                                menuOpen = false
                                templateName = value.text.lines().firstOrNull { it.isNotBlank() }.orEmpty().trim().take(40)
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            note?.let {
                Text(
                    formatNoteDate(it.createdAt),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            // Notepad (top half)
            // BasicTextField (styled as outlined) because it exposes the text layout for row navigation.
            val padSource = remember { MutableInteractionSource() }
            BasicTextField(
                value = value,
                onValueChange = {
                    // A text change by keyboard invalidates the alternatives; cursor moves don't.
                    if (it.text != value.text) dismissAlternatives()
                    viewModel.setValue(it)
                },
                modifier = Modifier.fillMaxWidth().weight(5f),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                interactionSource = padSource,
                onTextLayout = { textLayout = it },
                decorationBox = { inner ->
                    OutlinedTextFieldDefaults.DecorationBox(
                        value = value.text,
                        innerTextField = inner,
                        enabled = true,
                        singleLine = false,
                        visualTransformation = VisualTransformation.None,
                        interactionSource = padSource,
                        placeholder = { Text("Write a note…") },
                        container = {
                            OutlinedTextFieldDefaults.Container(enabled = true, isError = false, interactionSource = padSource)
                        }
                    )
                }
            )
            if (imeVisible) {
                TextButton(
                    onClick = { keyboard?.hide() },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Text("  Hide keyboard")
                }
            }
            // Alternatives for the last commit; tapping one replaces the inserted text
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).height(40.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                candidates.forEachIndexed { i, c ->
                    AssistChip(
                        onClick = {
                            inserted?.let { r ->
                                place(r.start, r.end, c)
                                selected = i
                            }
                        },
                        label = { Text(c) },
                        colors = if (i == selected) AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ) else AssistChipDefaults.assistChipColors())
                }
            }
            // Writing pad (bottom half)
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(4f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            ) {
                InkPad(
                    state = ink,
                    strokeWidth = thickness.widthPx,
                    // First touch while the keyboard is up only hides it; it must not draw.
                    onTouched = { if (imeVisible) { keyboard?.hide(); true } else false },
                    modifier = Modifier.fillMaxSize()
                )
                if (modelState != ModelState.Ready) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (modelState == ModelState.Offline) {
                            Text(
                                "Connect to the internet once to download the handwriting model",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Button(onClick = hw::prepare, modifier = Modifier.padding(top = 12.dp)) { Text("Retry") }
                        } else {
                            CircularProgressIndicator()
                            Text(
                                "Downloading handwriting model (one-time, ~20 MB)…",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                    }
                }
            }
            // The keyboard has its own backspace/space/enter, so these only show for handwriting.
            if (!imeVisible) Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Any button press ends the alternatives for the last commit.
                PadButton("✕ Clear", Modifier.weight(1f)) {
                    dismissAlternatives()
                    ink.clear()
                }
                // Space and new line first insert any handwriting still waiting to be recognized.
                PadButton("␣", Modifier.weight(1f)) {
                    scope.launch {
                        commitInk()
                        dismissAlternatives()
                        viewModel.setValue(viewModel.value.value.insert(" "))
                    }
                }
                PadButton("↵", Modifier.weight(1f)) {
                    scope.launch {
                        commitInk()
                        dismissAlternatives()
                        viewModel.setValue(viewModel.value.value.insert("\n"))
                    }
                }
                PadButton("↓", Modifier.weight(1f)) {
                    dismissAlternatives()
                    cursorToNextRowEnd()
                }
                RepeatPadButton("⌫", Modifier.weight(1f)) {
                    dismissAlternatives()
                    // With ink on the pad, backspace only wipes the ink.
                    if (!ink.isEmpty) ink.clear() else viewModel.setValue(viewModel.value.value.backspace())
                }
            }
        }
    }

    renameText?.let { name ->
        AlertDialog(
            onDismissRequest = { renameText = null },
            title = { Text("Rename note") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { renameText = it },
                    label = { Text("Name") },
                    supportingText = { Text("Leave empty to show the first line instead") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.rename(name)
                    renameText = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameText = null }) { Text("Cancel") } }
        )
    }

    templateName?.let { name ->
        AlertDialog(
            onDismissRequest = { templateName = null },
            title = { Text("Save as template") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { templateName = it },
                    label = { Text("Template name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        viewModel.saveAsTemplate(name)
                        templateName = null
                    }
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { templateName = null }) { Text("Cancel") } }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete note?") },
            text = { Text("This note will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onClose)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}
