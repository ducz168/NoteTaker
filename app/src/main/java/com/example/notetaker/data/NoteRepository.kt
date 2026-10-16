package com.example.notetaker.data

import kotlinx.coroutines.flow.Flow

class NoteRepository(private val dao: NoteDao) {
    fun observeAll(): Flow<List<Note>> = dao.observeAll()

    suspend fun get(id: Long): Note? = dao.getById(id)

    /** Inserts when [id] is 0, otherwise updates content. Returns the note id. */
    suspend fun save(id: Long, content: String, title: String = ""): Long {
        val now = System.currentTimeMillis()
        val existing = if (id == 0L) null else dao.getById(id)
        if (existing == null) {
            return dao.insert(Note(createdAt = now, updatedAt = now, content = content, title = title))
        }
        dao.update(existing.copy(content = content, title = title, updatedAt = now))
        return id
    }

    suspend fun delete(id: Long) = dao.deleteById(id)
}
