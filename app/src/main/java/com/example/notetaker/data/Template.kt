package com.example.notetaker.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Reusable starting text for a new note. Using one copies [content]; the template is never changed. */
@Entity
data class Template(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val content: String
)

@Dao
interface TemplateDao {
    @Query("SELECT * FROM Template ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Template>>

    @Query("SELECT * FROM Template WHERE id = :id")
    suspend fun getById(id: Long): Template?

    @Insert
    suspend fun insert(template: Template): Long

    @Query("DELETE FROM Template WHERE id = :id")
    suspend fun deleteById(id: Long)
}
