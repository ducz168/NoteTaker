package com.example.notetaker.ui

import android.app.Application
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.notetaker.data.AppDatabase
import com.example.notetaker.data.Note
import com.example.notetaker.data.NoteRepository
import com.example.notetaker.data.Template
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val AUTOSAVE_DELAY_MS = 1000L

/** [noteId] of 0 means a new, not-yet-saved note. */
class NoteEditViewModel(app: Application, noteId: Long, templateId: Long = 0L) : AndroidViewModel(app) {
    private val repo = NoteRepository(AppDatabase.get(app).noteDao())
    private val templateDao = AppDatabase.get(app).templateDao()

    private var currentId = noteId
    private var lastSaved: String? = null
    private var lastSavedTitle = ""
    private var deleted = false
    val note = MutableStateFlow<Note?>(null)
    val title = MutableStateFlow("")

    /** Text plus cursor/selection, so handwriting and the keyboard insert at the same place. */
    val value = MutableStateFlow(TextFieldValue(""))

    init {
        if (noteId != 0L) {
            viewModelScope.launch {
                repo.get(noteId)?.let {
                    note.value = it
                    lastSaved = it.content
                    lastSavedTitle = it.title
                    title.value = it.title
                    value.value = TextFieldValue(it.content, TextRange(it.content.length))
                }
            }
        } else if (templateId != 0L) {
            // New note from a template: start with its text. Untouched, it is not saved.
            viewModelScope.launch {
                templateDao.getById(templateId)?.let {
                    lastSaved = it.content
                    value.value = TextFieldValue(it.content, TextRange(it.content.length))
                }
            }
        }
        startAutosave()
    }

    fun saveAsTemplate(name: String) {
        val content = value.value.text
        if (content.isBlank() || name.isBlank()) return
        viewModelScope.launch { templateDao.insert(Template(name = name.trim(), content = content)) }
    }

    @OptIn(FlowPreview::class)
    private fun startAutosave() {
        viewModelScope.launch {
            combine(value.map { it.text }, title) { text, name -> text to name }
                .distinctUntilChanged()
                .debounce(AUTOSAVE_DELAY_MS)
                .collect { persist() }
        }
    }

    /** Saves the current text and title unless unchanged, the note was deleted, or it is a new empty note. */
    private suspend fun persist() {
        val content = value.value.text
        val name = title.value
        if (deleted || (content == lastSaved && name == lastSavedTitle)) return
        if (currentId == 0L && content.isBlank()) return
        currentId = repo.save(currentId, content, name)
        lastSaved = content
        lastSavedTitle = name
    }

    fun setValue(v: TextFieldValue) { value.value = v }

    /** Empty [name] removes the title, so the list falls back to the note's first line. */
    fun rename(name: String) { title.value = name.trim() }

    /** Saves the note (skipping empty new notes), then runs [done]. */
    fun save(done: () -> Unit) {
        viewModelScope.launch {
            persist()
            done()
        }
    }

    fun delete(done: () -> Unit) {
        deleted = true
        viewModelScope.launch {
            if (currentId != 0L) repo.delete(currentId)
            done()
        }
    }
}
