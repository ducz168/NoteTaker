package com.example.notetaker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.notetaker.data.AppDatabase
import com.example.notetaker.data.Note
import com.example.notetaker.data.NoteRepository
import com.example.notetaker.data.Template
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotesListViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = NoteRepository(AppDatabase.get(app).noteDao())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    val notes: StateFlow<List<Note>> = combine(repo.observeAll(), _query) { all, q ->
        val needle = q.trim()
        if (needle.isEmpty()) all else all.filter {
            it.content.contains(needle, ignoreCase = true) || it.title.contains(needle, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val templateDao = AppDatabase.get(app).templateDao()
    val templates: StateFlow<List<Template>> = templateDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteTemplate(id: Long) {
        viewModelScope.launch { templateDao.deleteById(id) }
    }

    fun setQuery(value: String) { _query.value = value }

    fun delete(id: Long) {
        viewModelScope.launch { repo.delete(id) }
    }
}
