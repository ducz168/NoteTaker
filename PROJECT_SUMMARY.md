# Note Taker v2: project summary (for analysis)

Personal-use Android handwriting notepad. Kotlin, Jetpack Compose (Material 3), MVVM.
Package `com.example.notetaker`, minSdk 26, target/compile SDK 37, AGP 9.4.1, Kotlin 2.2.10.
Target device: Samsung Galaxy S24+ (Android 14+), portrait only.
The original build spec is `note-taker-spec.md`. The app replaced a Whisper voice-to-text app; all voice code was deleted.

## What it does
Each note screen is split in two. The top half is the notepad (text). The bottom half is an ink pad.
Handwriting is recognized offline (ML Kit Digital Ink, English) and inserted as typed text at the notepad cursor.

## Stack
- Room: `Note` and `Template` entities, DB version 3, migrations 1->2 (Template table) and 2->3 (`Note.title`).
- DataStore: settings (commit delay, ink thickness).
- ML Kit `com.google.mlkit:digital-ink-recognition:19.0.0`. Classes live in `...digitalink.recognition.*` (moved in 19.x).
- Navigation Compose. Routes: `list`, `settings`, `note/{id}?template={template}`.
- Only permission: `INTERNET`, used for the one-time model download.

## Source map (`app/src/main/java/com/example/notetaker/`)
| File | Role |
|---|---|
| `MainActivity.kt` | NavHost and routes |
| `ui/NotesListScreen.kt`, `NotesListViewModel.kt` | List (name + line count), search (text and title), delete with confirm, **+** with Blank/Template chooser |
| `ui/NoteEditScreen.kt` | Note screen: layout, commit/insert logic, alternatives row, buttons, menu (Share, Rename, Save as template) |
| `ui/NoteEditViewModel.kt` | `TextFieldValue` state (text + cursor), title, debounced autosave (1 s), template prefill |
| `ui/InkPad.kt` | Pointer-input ink capture (points with timestamps), smooth path drawing, `InkState`, `toMlKitInk` |
| `ui/HandwritingViewModel.kt` | Model state machine (Checking/Downloading/Ready/Offline), download, recognizer, `recognize()` |
| `ui/TextInsert.kt` | `withAutoSpace`, `insert`, `backspace` (surrogate-safe), `InsertedRange` |
| `ui/SettingsScreen.kt`, `data/SettingsRepository.kt` | Commit delay 0.5-3.0 s (default 1.0), ink thickness, model status/download |
| `data/Note.kt`, `Template.kt`, `NoteDao.kt`, `NoteRepository.kt`, `AppDatabase.kt` | Persistence |

## Key behavior
- **Commit:** `LaunchedEffect(ink.activity, modelState)` waits `commitDelay` after the last stroke ends. `commitInk()` (Mutex-serialized) recognizes the strokes present at that moment, inserts the top candidate, then `ink.removeFirst(count)`. Strokes written meanwhile are kept for the next round. The recognize/insert step runs under `NonCancellable`.
- **Insert:** goes at the cursor, or replaces a selection. Auto-space is added when the text before the cursor is non-empty and not whitespace, unless the new text starts with `. , ! ? ; :`.
- **Alternatives:** up to 4 candidates. Tapping one replaces the tracked `InsertedRange`. They are dismissed on any keyboard text change or button press. Cursor-only moves keep them.
- **Buttons** (hidden while the IME is visible): Clear pad, Space, New line, ↓ (cursor to end of next visual row via `TextLayoutResult`), Backspace (hold to repeat; clears ink first if the pad has any). Space and New line call `commitInk()` first so pending handwriting lands before the whitespace.
- **Keyboard and pad:** the notepad is a `BasicTextField` decorated as outlined (needed for `onTextLayout`). Touching the pad while the IME is open only hides it and swallows that gesture, so no stray dot.
- **Model:** downloaded on first use via `RemoteModelManager`. While missing or offline the pad is overlaid with a message and Retry. Notes and keyboard editing still work.
- **Autosave:** debounced on text or title change, and on leaving. Empty new notes are never saved. An unmodified template-prefilled note is not saved. A deleted note cannot be resaved.
- **Templates:** Save as template (⋮) copies the text. New note from a template copies the template text. Templates are never modified.
- **Rename:** optional `Note.title`. An empty title falls back to the first line in the list.
- **Share:** `ACTION_SEND` text/plain of "title\n\ncontent" (or just the content if untitled).

## Build and run
- Use Android Studio's JDK: `JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`. It is not set globally.
- `./gradlew assembleDebug` or `installDebug`; `assembleRelease` / `installRelease`.
- Release has R8 enabled and is signed with the debug key (personal use). APK is about 30.6 MB (debug about 42.8 MB).
- **R8 gotcha:** ML Kit's reflective model-identifier setup crashed in release until keep rules were added in `app/src/main/keepRules/rules.keep` (`com.google.mlkit.**`, `com.google.android.gms.internal.mlkit_**`, ViewModel constructors). Any new reflective dependency may need the same.

## Known limitations and ideas to analyze
- No instrumented or unit tests beyond the template stubs; the checklist in the spec was verified manually on a device.
- `NoteEditScreen.kt` is large (layout, commit logic and dialogs in one composable). A candidate for extracting a state holder.
- Recognition uses the top candidate only; no language-model or dictionary post-processing.
- `rememberSwipeToDismissBoxState(confirmValueChange=...)` in the list is deprecated (warning only).
- Release is debug-signed; a real keystore would be needed for any distribution.
- Out of scope per spec: voice input, other languages, export/sync (only text Share), saving handwriting images, gesture editing.
- Possible unverified areas: stylus feel, rare IME/pad layout edge cases when the keyboard is open, behavior with very long notes.
