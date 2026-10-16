package com.example.notetaker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.digitalink.recognition.Ink

/** Builds an ML Kit [Ink] from the first [count] strokes. */
fun InkState.toMlKitInk(count: Int): Ink {
    val ink = Ink.builder()
    for (s in strokes.take(count)) {
        val b = Ink.Stroke.builder()
        for (p in s.points) b.addPoint(Ink.Point.create(p.x, p.y, p.t))
        ink.addStroke(b.build())
    }
    return ink.build()
}

/** One touch point, kept with its timestamp so it can be fed to ML Kit later. */
class InkPoint(val x: Float, val y: Float, val t: Long)

class InkStroke {
    val points = ArrayList<InkPoint>()
}

/** Strokes currently on the pad. [version] changes whenever they do, to trigger redraws. */
@Stable
class InkState {
    val strokes = ArrayList<InkStroke>()
    var version by mutableIntStateOf(0)
        private set

    /** Changes when a stroke starts or ends or the pad is cleared (not on every point). */
    var activity by mutableIntStateOf(0)
        private set
    var isDrawing by mutableStateOf(false)
        private set

    /** Pad size in px, set by [InkPad]. Needed for ML Kit's writing area. */
    var width = 0f
    var height = 0f

    val isEmpty get() = strokes.isEmpty()

    fun begin(p: InkPoint) {
        strokes.add(InkStroke().also { it.points.add(p) })
        isDrawing = true
        version++
        activity++
    }

    fun extend(p: InkPoint) {
        strokes.lastOrNull()?.points?.add(p)
        version++
    }

    fun end() {
        isDrawing = false
        activity++
    }

    fun clear() {
        strokes.clear()
        version++
        activity++
    }

    /** Drops the first [n] strokes (the ones that were just recognized), keeping any written since. */
    fun removeFirst(n: Int) {
        repeat(minOf(n, strokes.size)) { strokes.removeAt(0) }
        version++
        activity++
    }
}

@Composable
fun InkPad(
    state: InkState,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 6f,
    /** Called on touch-down. Return true to swallow the whole gesture (no ink). */
    onTouched: () -> Boolean = { false }
) {
    val currentOnTouched by rememberUpdatedState(onTouched)
    val inkColor = MaterialTheme.colorScheme.onSurface
    val guideColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    if (currentOnTouched()) {
                        // Consume the rest of this touch so it can't leave a dot.
                        do {
                            val e = awaitPointerEvent()
                            e.changes.forEach { it.consume() }
                        } while (e.changes.any { it.pressed })
                        return@awaitEachGesture
                    }
                    state.begin(InkPoint(down.position.x, down.position.y, down.uptimeMillis))
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Release) break
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            // Include the intermediate samples for a smoother line.
                            for (h in change.historical) {
                                state.extend(InkPoint(h.position.x, h.position.y, h.uptimeMillis))
                            }
                            state.extend(InkPoint(change.position.x, change.position.y, change.uptimeMillis))
                            change.consume()
                        }
                    } finally {
                        state.end()
                    }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            state.width = size.width
            state.height = size.height
            state.version // read so the canvas redraws as strokes grow
            val mid = size.height / 2
            drawLine(guideColor, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 2f)

            val style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            for (stroke in state.strokes) {
                val pts = stroke.points
                if (pts.size == 1) {
                    drawCircle(inkColor, strokeWidth / 2, Offset(pts[0].x, pts[0].y))
                    continue
                }
                val path = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (i in 1 until pts.size) {
                        val a = pts[i - 1]
                        val b = pts[i]
                        quadraticTo(a.x, a.y, (a.x + b.x) / 2, (a.y + b.y) / 2)
                    }
                    lineTo(pts.last().x, pts.last().y)
                }
                drawPath(path, inkColor, style = style)
            }
        }
        if (state.isEmpty) {
            Text(
                "Write here — one line at a time",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.Center).padding(16.dp)
            )
        }
    }
}
