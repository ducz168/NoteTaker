package com.example.notetaker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long, // epoch millis, shown as the note's date & time
    val updatedAt: Long,
    val content: String, // lines joined with "\n"
    @ColumnInfo(defaultValue = "''") val title: String = "" // optional name; empty = show the first line
)
