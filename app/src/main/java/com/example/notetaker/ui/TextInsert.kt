package com.example.notetaker.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

private const val NO_SPACE_BEFORE = ".,!?;:"

/** Prefixes [piece] with a space unless [before] ends in whitespace/is empty or [piece] starts with punctuation. */
fun withAutoSpace(before: String, piece: String): String =
    if (before.isNotEmpty() && !before.last().isWhitespace() && piece.first() !in NO_SPACE_BEFORE) " $piece" else piece

/** Replaces the selection (or inserts at the cursor) with [s] and puts the cursor after it. */
fun TextFieldValue.insert(s: String): TextFieldValue {
    val start = selection.min
    return TextFieldValue(text.replaceRange(start, selection.max, s), TextRange(start + s.length))
}

/** Deletes the selection, or one character (a whole surrogate pair) before the cursor. */
fun TextFieldValue.backspace(): TextFieldValue {
    if (!selection.collapsed) return insert("")
    val pos = selection.start
    if (pos == 0) return this
    val from = if (pos >= 2 && text[pos - 1].isLowSurrogate() && text[pos - 2].isHighSurrogate()) pos - 2 else pos - 1
    return TextFieldValue(text.removeRange(from, pos), TextRange(from))
}

/** Where the last handwriting commit landed, including any auto-added leading space. */
data class InsertedRange(val start: Int, val end: Int)
