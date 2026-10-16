# Note Taker v2 — Handwriting Notepad — Build Spec for Claude Code (Android Studio)

## 0. Context: read first
This is an **existing project**. The previous version of Note Taker was a voice-to-text app (Whisper + microphone).
That approach is being **dropped completely** and replaced by handwriting input.
- **Keep:** notes list, Room database and `Note` entity, note detail/edit screen, settings screen + DataStore, app name "Note Taker", package name.
- **Remove:** everything voice-related (see Phase 0).
- No backup is needed. Edit the project in place.

## 1. What we're building
A notepad app where each note screen is split in two:
- **Top half:** the note text (notepad).
- **Bottom half:** a writing pad where the user writes with a finger or stylus.
The handwriting is recognized offline and inserted as typed text into the notepad at the cursor.
It works like a keyboard, except you draw letters instead of tapping them.

Target device: Samsung Galaxy S24+ (Android 14+). Portrait orientation only. Personal use.

## 2. Tech stack
- Kotlin, Jetpack Compose (Material 3), MVVM, Coroutines/Flow (keep what the project already uses)
- Room (existing), DataStore (existing)
- **Google ML Kit Digital Ink Recognition:** `com.google.mlkit:digital-ink-recognition` (latest stable)
  - Model: `DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US")`
  - Downloaded once via `RemoteModelManager`, then fully offline.

## 3. Permissions
- `INTERNET` is needed **only** for the one-time model download. All recognition runs on-device.
- Remove `RECORD_AUDIO`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE` and any other voice-related permissions.

## 4. Note screen layout
```
┌─────────────────────────────┐
│  ← Back     Note Taker   ⋮  │  top bar (date & time of note)
├─────────────────────────────┤
│                             │
│  Notepad (editable text,    │  ~50% height, scrollable
│  cursor visible)            │
│                             │
├─────────────────────────────┤
│ [guess1] [guess2] [guess3]  │  alternatives row
├─────────────────────────────┤
│                             │
│   Writing pad (ink canvas)  │  ~40% height
│                             │
├─────────────────────────────┤
│  [⌫ Backspace] [␣ Space] [↵ New line] [✕ Clear pad] │
└─────────────────────────────┘
```

### Notepad (top)
- A multi-line `TextField`/`BasicTextField` with a visible cursor, scrollable.
- Tapping the text places the cursor **and** opens the system keyboard for quick fixes.
- When the keyboard is open, show a small "hide keyboard" (✎ handwriting) button. Touching the writing pad also hides the keyboard.
- Text from the keyboard and from handwriting both go in at the cursor position.

### Writing pad (bottom)
- A Compose `Canvas` that captures touch/stylus input via pointer events and draws smooth ink strokes (paths with rounded caps and joins).
- Light hint text when empty: "Write here — one line at a time".
- A thin guideline across the middle helps users write on a single line.
- Record each stroke as points (x, y, timestamp) for ML Kit.

## 5. Recognition and insert logic
1. While the user writes, collect strokes into an `Ink`.
2. When **no new stroke** starts for `commitDelay` (setting, default **1.0 s**, range 0.5–3.0 s), run recognition.
3. Call `recognizer.recognize(ink, RecognitionContext)` with:
   - `preContext` = up to the last 20 characters before the cursor (improves accuracy)
   - `WritingArea(padWidth, padHeight)`
4. Take the **top candidate** and insert it at the cursor:
   - **Auto-spacing:** add a space before the text if the character before the cursor is not whitespace or the start of a line. Don't add a space before punctuation (`. , ! ? ; :`).
5. Clear the pad.
6. Show up to **4 candidates** in the alternatives row. The first is highlighted as the one inserted.
7. Tapping an alternative **replaces** the text that was just inserted (track the inserted range). The alternatives stay until the next commit, a manual edit, or a button press.
- Empty or blank results: insert nothing, clear the pad.
- If a new stroke starts while recognition is running, don't lose it. Queue it into the next ink.
- Keep all recognition off the main thread so writing never lags.

### Buttons
- **Backspace:** deletes one character before the cursor (long press = repeat). If there is ink on the pad, clear the pad instead.
- **Space:** inserts a space at the cursor.
- **New line:** inserts `\n` at the cursor.
- **Clear pad:** discards the current strokes without recognizing them.
- Make all buttons large enough to hit easily with a thumb.

## 6. Model download (first launch)
- On first launch, or when the model is missing, show a screen/dialog: "Downloading handwriting model (one-time, ~20 MB)…" with progress or an indeterminate indicator.
- If offline, show: "Connect to the internet once to download the handwriting model" with a Retry button.
- After download, the app never needs the internet again.
- The notes list and keyboard editing still work while the model is missing. Only the pad is disabled, with a message.

## 7. Notes and saving
- Keep the existing `Note` entity (`id`, `createdAt`, `updatedAt`, `content`). Remove any voice-only fields if present.
- The notes list (existing): newest first, date and time, first-line preview, search, delete with confirmation.
- The **+ (New note)** button opens the note screen with an empty note. Replace the old Record button.
- **Autosave:** after each commit/edit (debounced ~1 s) and when leaving the screen. Don't save notes that are still empty.

## 8. Settings
- **Remove:** auto new-line pause, max recording duration, Whisper model info.
- **Add:**
  - Commit delay: slider 0.5–3.0 s (default 1.0 s)
  - Ink thickness: Thin / Medium / Thick (default Medium)
  - Handwriting model status: "Downloaded" / "Not downloaded" + Download button

## 9. Build phases (do one at a time, stop after each so I can test)
**Phase 0: Remove voice features**
- Delete: whisper.cpp source, JNI/NDK/CMake config, `externalNativeBuild` in Gradle, `.so` libs, the bundled `ggml-*.bin` model in `assets/`, Silero VAD dependency, AudioRecord code, foreground recording service, recording screen, voice-related settings, and their navigation routes.
- Remove the voice permissions from the manifest.
- The app must build and run: notes list + keyboard editing still work.

**Phase 1: Split screen + writing pad**
- New note screen layout (notepad top, pad bottom, buttons). Smooth ink drawing. Clear pad works. No recognition yet.

**Phase 2: Handwriting recognition**
- ML Kit dependency, model download flow, recognition after the commit delay. Log/show the results.

**Phase 3: Insert into notepad**
- Insert at cursor with auto-spacing, alternatives row with replace, Backspace/Space/New line, keyboard quick-fix switching, autosave.

**Phase 4: Settings + polish**
- Settings changes, ink thickness, edge cases, acceptance checklist.

## 10. Out of scope
- Voice input of any kind
- Languages other than English
- Export/share/sync
- Saving handwriting images (only the text is kept)
- Gesture editing (scribble-to-delete, etc.)

## 11. Acceptance checks
- [ ] No Whisper/NDK/audio code, model file or microphone permission remains. The APK size has dropped noticeably.
- [ ] Airplane mode (after the model download): handwriting recognition works.
- [ ] Writing "hello" and pausing 1 s inserts "hello" at the cursor and clears the pad.
- [ ] Writing "the year is 2001" on one line inserts the full phrase.
- [ ] Two commits in a row get a space between them. No space is added before "." or ",".
- [ ] Tapping an alternative replaces the last inserted text correctly.
- [ ] Backspace, Space and New line work at the cursor position, including mid-text.
- [ ] Tapping the notepad opens the keyboard. Touching the pad hides it. Both input methods insert at the same cursor.
- [ ] Moving the cursor to the middle of a note and writing inserts the text there.
- [ ] Notes persist after closing and reopening the app. Empty notes aren't saved.
- [ ] Writing feels smooth with no lag, with a finger and with a capacitive stylus.
