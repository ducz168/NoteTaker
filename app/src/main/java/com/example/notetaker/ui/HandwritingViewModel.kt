package com.example.notetaker.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

private const val TAG = "Handwriting"

sealed interface ModelState {
    data object Checking : ModelState
    data object Downloading : ModelState
    data object Ready : ModelState
    /** Download failed, most likely because the phone is offline. */
    data object Offline : ModelState
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}

/** Owns the on-device handwriting model: download state and the recognizer. */
class HandwritingViewModel(app: Application) : AndroidViewModel(app) {
    private val model: DigitalInkRecognitionModel = DigitalInkRecognitionModel.builder(
        DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US")!!
    ).build()
    private val manager = RemoteModelManager.getInstance()
    private var recognizer: DigitalInkRecognizer? = null

    private val _state = MutableStateFlow<ModelState>(ModelState.Checking)
    val state: StateFlow<ModelState> = _state

    init { prepare() }

    /** Checks for the model and downloads it if missing. Also used as "Retry". */
    fun prepare() {
        viewModelScope.launch {
            _state.value = ModelState.Checking
            try {
                if (!manager.isModelDownloaded(model).await()) {
                    _state.value = ModelState.Downloading
                    Log.d(TAG, "Downloading model")
                    manager.download(model, DownloadConditions.Builder().build()).await()
                }
                recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())
                Log.d(TAG, "Model ready")
                _state.value = ModelState.Ready
            } catch (e: Exception) {
                Log.w(TAG, "Model download failed", e)
                _state.value = ModelState.Offline
            }
        }
    }

    /** Returns candidates, best first. Runs off the main thread inside ML Kit. */
    suspend fun recognize(ink: Ink, preContext: String, width: Float, height: Float): List<String> {
        val r = recognizer ?: return emptyList()
        val context = RecognitionContext.builder()
            .setPreContext(preContext)
            .setWritingArea(WritingArea(width, height))
            .build()
        return try {
            r.recognize(ink, context).await().candidates.map { it.text }
        } catch (e: Exception) {
            Log.w(TAG, "Recognition failed", e)
            emptyList()
        }
    }

    override fun onCleared() {
        recognizer?.close()
    }
}
