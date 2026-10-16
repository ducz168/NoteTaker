package com.example.notetaker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class InkThickness(val label: String, val widthPx: Float) {
    Thin("Thin", 4f),
    Medium("Medium", 6f),
    Thick("Thick", 10f)
}

class SettingsRepository(context: Context) {
    companion object {
        const val MIN_COMMIT_DELAY_SEC = 0.5f
        const val MAX_COMMIT_DELAY_SEC = 3.0f
        const val DEFAULT_COMMIT_DELAY_SEC = 1.0f
        private val DELAY_KEY = floatPreferencesKey("commit_delay_sec")
        private val THICKNESS_KEY = stringPreferencesKey("ink_thickness")
    }

    private val store = context.applicationContext.dataStore

    /** Pause after the last stroke before handwriting is recognized. */
    val commitDelaySec: Flow<Float> = store.data.map {
        (it[DELAY_KEY] ?: DEFAULT_COMMIT_DELAY_SEC).coerceIn(MIN_COMMIT_DELAY_SEC, MAX_COMMIT_DELAY_SEC)
    }

    val inkThickness: Flow<InkThickness> = store.data.map { prefs ->
        InkThickness.entries.firstOrNull { it.name == prefs[THICKNESS_KEY] } ?: InkThickness.Medium
    }

    suspend fun setCommitDelaySec(value: Float) {
        store.edit { it[DELAY_KEY] = value.coerceIn(MIN_COMMIT_DELAY_SEC, MAX_COMMIT_DELAY_SEC) }
    }

    suspend fun setInkThickness(value: InkThickness) {
        store.edit { it[THICKNESS_KEY] = value.name }
    }
}
