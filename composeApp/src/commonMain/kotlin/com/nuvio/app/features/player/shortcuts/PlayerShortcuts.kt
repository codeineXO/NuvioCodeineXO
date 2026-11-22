package com.nuvio.app.features.player.shortcuts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A key plus modifiers. [code] uses DOM `KeyboardEvent.code` names ("KeyJ", "ArrowLeft", "Space")
 * because the desktop player controls run in a web view and match on those directly.
 */
data class KeyChord(
    val code: String,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
) {
    fun serialize(): String = buildString {
        if (ctrl) append("Ctrl+")
        if (alt) append("Alt+")
        if (shift) append("Shift+")
        append(code)
    }

    val isReserved: Boolean
        get() = code in ReservedCodes || (ctrl && code in ReservedCtrlCodes)

    override fun toString(): String = serialize()

    companion object {
        private val ReservedCodes = setOf("Escape", "Backspace", "Enter", "NumpadEnter", "Tab", "F11")
        private val ReservedCtrlCodes = setOf("Equal", "Minus", "Digit0", "Numpad0", "NumpadAdd", "NumpadSubtract")

        fun parse(text: String): KeyChord? {
            val parts = text.trim().split('+').filter { it.isNotEmpty() }
            if (parts.isEmpty()) return null
            val code = parts.last()
            val modifiers = parts.dropLast(1)
            if (modifiers.any { it !in setOf("Ctrl", "Alt", "Shift") }) return null
            return KeyChord(code, ctrl = "Ctrl" in modifiers, alt = "Alt" in modifiers, shift = "Shift" in modifiers)
        }
    }
}

enum class PlayerShortcutGroup { Playback, Tracks, Panels, View }

/** Every rebindable player shortcut; [id] is the action name the controls script dispatches on. */
enum class PlayerShortcutAction(
    val id: String,
    val group: PlayerShortcutGroup,
    val defaults: List<String>,
) {
    TogglePlay("togglePlay", PlayerShortcutGroup.Playback, listOf("Space", "KeyK")),
    SeekBack("seekBack", PlayerShortcutGroup.Playback, listOf("ArrowLeft")),
    SeekForward("seekForward", PlayerShortcutGroup.Playback, listOf("ArrowRight")),
    SeekBackLong("seekBackLong", PlayerShortcutGroup.Playback, listOf("KeyJ")),
    SeekForwardLong("seekForwardLong", PlayerShortcutGroup.Playback, listOf("KeyL")),
    SeekBackFine("seekBackFine", PlayerShortcutGroup.Playback, listOf("Shift+ArrowLeft", "Shift+KeyJ")),
    SeekForwardFine("seekForwardFine", PlayerShortcutGroup.Playback, listOf("Shift+ArrowRight", "Shift+KeyL")),
    FrameBack("frameBack", PlayerShortcutGroup.Playback, listOf("Comma")),
    FrameForward("frameForward", PlayerShortcutGroup.Playback, listOf("Period")),
    VolumeUp("volumeUp", PlayerShortcutGroup.Playback, listOf("ArrowUp")),
    VolumeDown("volumeDown", PlayerShortcutGroup.Playback, listOf("ArrowDown")),
    Mute("mute", PlayerShortcutGroup.Playback, listOf("KeyM")),
    SpeedDown("speedDown", PlayerShortcutGroup.Playback, listOf("Shift+Comma")),
    SpeedUp("speedUp", PlayerShortcutGroup.Playback, listOf("Shift+Period")),
    SpeedReset("speedReset", PlayerShortcutGroup.Playback, listOf("Slash")),
    NextEpisode("nextEpisode", PlayerShortcutGroup.Playback, listOf("KeyN")),
    CycleAudio("cycleAudio", PlayerShortcutGroup.Tracks, listOf("Shift+KeyB")),
    CycleSubtitles("cycleSubtitles", PlayerShortcutGroup.Tracks, listOf("KeyB")),
    ToggleSubtitles("toggleSubtitles", PlayerShortcutGroup.Tracks, listOf("KeyV")),
    SubtitleDelayDown("subtitleDelayDown", PlayerShortcutGroup.Tracks, listOf("KeyG")),
    SubtitleDelayUp("subtitleDelayUp", PlayerShortcutGroup.Tracks, listOf("KeyH")),
    SubtitleOpacity("subtitleOpacity", PlayerShortcutGroup.Tracks, listOf("KeyO")),
    AudioPanel("audioPanel", PlayerShortcutGroup.Panels, listOf("KeyA")),
    SubtitlesPanel("subtitlesPanel", PlayerShortcutGroup.Panels, listOf("KeyS")),
    SpeedPanel("speedPanel", PlayerShortcutGroup.Panels, listOf("Backquote")),
    QualityPanel("qualityPanel", PlayerShortcutGroup.Panels, listOf("KeyD")),
    EpisodesPanel("episodesPanel", PlayerShortcutGroup.Panels, listOf("KeyE")),
    SourcesPanel("sourcesPanel", PlayerShortcutGroup.Panels, listOf("KeyQ")),
    ChaptersPanel("chaptersPanel", PlayerShortcutGroup.Panels, listOf("KeyC")),
    ShadersPanel("shadersPanel", PlayerShortcutGroup.Panels, listOf("KeyU")),
    EyeFilterPanel("eyeFilterPanel", PlayerShortcutGroup.Panels, listOf("Equal")),
    PartyPanel("partyPanel", PlayerShortcutGroup.Panels, listOf("KeyW")),
    Fullscreen("fullscreen", PlayerShortcutGroup.View, listOf("KeyF")),
    MiniPlayer("miniPlayer", PlayerShortcutGroup.View, listOf("KeyY")),
    PictureInPicture("pictureInPicture", PlayerShortcutGroup.View, listOf("KeyP")),
    Resize("resize", PlayerShortcutGroup.View, listOf("KeyR")),
    StreamStats("streamStats", PlayerShortcutGroup.View, listOf("KeyI")),
    EyeFilterToggle("eyeFilterToggle", PlayerShortcutGroup.View, listOf("Digit0", "Numpad0")),
    GrainFilterToggle("grainFilterToggle", PlayerShortcutGroup.View, listOf("Digit9", "Numpad9")),
    Screenshot("screenshot", PlayerShortcutGroup.View, listOf("Shift+KeyS")),
    ToggleDebugLog("toggleDebugLog", PlayerShortcutGroup.View, listOf("Ctrl+Shift+KeyL")),
    CheatSheet("cheatSheet", PlayerShortcutGroup.View, listOf("F1")),
    ;

    val defaultChords: List<KeyChord?>
        get() = List(SLOTS) { slot -> defaults.getOrNull(slot)?.let(KeyChord::parse) }

    companion object {
        const val SLOTS = 2
        fun byId(id: String): PlayerShortcutAction? = entries.firstOrNull { it.id == id }
    }
}

/** Two binding slots per action. Only actions that differ from their defaults are stored. */
data class PlayerKeymap(val overrides: Map<PlayerShortcutAction, List<KeyChord?>> = emptyMap()) {

    fun bindings(action: PlayerShortcutAction): List<KeyChord?> = overrides[action] ?: action.defaultChords

    fun isCustomized(action: PlayerShortcutAction): Boolean = action in overrides

    fun actionFor(chord: KeyChord): PlayerShortcutAction? =
        PlayerShortcutAction.entries.firstOrNull { chord in bindings(it) }

    /** The other action already using [chord], if any. */
    fun conflict(chord: KeyChord, action: PlayerShortcutAction, slot: Int): Pair<PlayerShortcutAction, Int>? {
        for (other in PlayerShortcutAction.entries) {
            val index = bindings(other).indexOf(chord)
            if (index >= 0 && !(other == action && index == slot)) return other to index
        }
        return null
    }

    /** Assigns [chord] to [action]'s [slot]; any other holder of the chord gets the slot's previous key (swap). */
    fun assign(action: PlayerShortcutAction, slot: Int, chord: KeyChord?): PlayerKeymap {
        val previous = bindings(action).getOrNull(slot)
        var next = this
        if (chord != null) {
            conflict(chord, action, slot)?.let { (other, otherSlot) ->
                next = next.withSlot(other, otherSlot, if (other == action) null else previous)
            }
        }
        return next.withSlot(action, slot, chord)
    }

    fun reset(action: PlayerShortcutAction): PlayerKeymap = PlayerKeymap(overrides - action)

    private fun withSlot(action: PlayerShortcutAction, slot: Int, chord: KeyChord?): PlayerKeymap {
        val updated = bindings(action).toMutableList().also { it[slot] = chord }
        val overrides = if (updated == action.defaultChords) overrides - action else overrides + (action to updated)
        return PlayerKeymap(overrides)
    }

    fun serialize(): String = overrides.entries.joinToString(";") { (action, chords) ->
        action.id + "=" + chords.joinToString("|") { it?.serialize().orEmpty() }
    }

    companion object {
        fun parse(text: String?): PlayerKeymap {
            if (text.isNullOrBlank()) return PlayerKeymap()
            val overrides = text.split(';').mapNotNull { entry ->
                val action = PlayerShortcutAction.byId(entry.substringBefore('=')) ?: return@mapNotNull null
                val chords = entry.substringAfter('=', "").split('|')
                    .map { KeyChord.parse(it)?.takeUnless(KeyChord::isReserved) }
                action to List(PlayerShortcutAction.SLOTS) { chords.getOrNull(it) }
            }.toMap()
            return PlayerKeymap(overrides)
        }
    }
}

data class PlayerShortcutSteps(
    val seekShortMs: Int = 3_000,
    val seekLongMs: Int = 10_000,
    val seekFineMs: Int = 1_000,
    val volumePercent: Int = 5,
    val subtitleDelayMs: Int = 100,
) {
    fun serialize(): String = "$seekShortMs,$seekLongMs,$seekFineMs,$volumePercent,$subtitleDelayMs"

    companion object {
        val SeekShortRange = 1_000..30_000
        val SeekLongRange = 5_000..120_000
        val SeekFineRange = 500..5_000
        val VolumeRange = 1..20
        val SubtitleDelayRange = 50..1_000

        fun parse(text: String?): PlayerShortcutSteps {
            val values = text?.split(',')?.map { it.trim().toIntOrNull() } ?: return PlayerShortcutSteps()
            val defaults = PlayerShortcutSteps()
            return PlayerShortcutSteps(
                seekShortMs = values.getOrNull(0)?.coerceIn(SeekShortRange) ?: defaults.seekShortMs,
                seekLongMs = values.getOrNull(1)?.coerceIn(SeekLongRange) ?: defaults.seekLongMs,
                seekFineMs = values.getOrNull(2)?.coerceIn(SeekFineRange) ?: defaults.seekFineMs,
                volumePercent = values.getOrNull(3)?.coerceIn(VolumeRange) ?: defaults.volumePercent,
                subtitleDelayMs = values.getOrNull(4)?.coerceIn(SubtitleDelayRange) ?: defaults.subtitleDelayMs,
            )
        }
    }
}

/** Device-wide player keymap and step sizes. */
object PlayerShortcutSettings {
    private val mutableKeymap = MutableStateFlow(PlayerKeymap())
    val keymap: StateFlow<PlayerKeymap> = mutableKeymap.asStateFlow()
    private val mutableSteps = MutableStateFlow(PlayerShortcutSteps())
    val steps: StateFlow<PlayerShortcutSteps> = mutableSteps.asStateFlow()
    private var persist: (key: String, value: String?) -> Unit = { _, _ -> }

    private const val KEYMAP_KEY = "keymap"
    private const val STEPS_KEY = "steps"

    fun initialize(load: (String) -> String?, save: (String, String?) -> Unit) {
        mutableKeymap.value = PlayerKeymap.parse(load(KEYMAP_KEY))
        mutableSteps.value = PlayerShortcutSteps.parse(load(STEPS_KEY))
        persist = save
    }

    fun setKeymap(keymap: PlayerKeymap) {
        mutableKeymap.value = keymap
        persist(KEYMAP_KEY, keymap.serialize().ifEmpty { null })
    }

    fun setSteps(steps: PlayerShortcutSteps) {
        mutableSteps.value = steps
        persist(STEPS_KEY, steps.takeIf { it != PlayerShortcutSteps() }?.serialize())
    }

    fun resetAll() {
        setKeymap(PlayerKeymap())
        setSteps(PlayerShortcutSteps())
    }
}

/** Routes the next key press to the Shortcuts page while it is recording a new binding. */
object PlayerShortcutCapture {
    private val mutableActive = MutableStateFlow(false)
    val active: StateFlow<Boolean> = mutableActive.asStateFlow()
    private var sink: ((KeyChord?) -> Unit)? = null

    val isCapturing: Boolean get() = sink != null

    fun begin(onResult: (KeyChord?) -> Unit) {
        sink = onResult
        mutableActive.value = true
    }

    fun cancel() = deliver(null)

    /** A null [chord] means the user cancelled. */
    fun deliver(chord: KeyChord?) {
        val target = sink ?: return
        sink = null
        mutableActive.value = false
        target(chord)
    }
}

/** Short key label for display: "Shift + J", "←", "Space". */
fun KeyChord.displayLabel(): String = buildList {
    if (ctrl) add("Ctrl")
    if (alt) add("Alt")
    if (shift) add("Shift")
    add(codeLabel(code))
}.joinToString(" + ")

fun codeLabel(code: String): String = when {
    code.startsWith("Key") && code.length == 4 -> code.substring(3)
    code.startsWith("Digit") -> code.removePrefix("Digit")
    code.startsWith("Numpad") && code.length == 7 && code[6].isDigit() -> "Num " + code[6]
    else -> when (code) {
        "ArrowLeft" -> "←"
        "ArrowRight" -> "→"
        "ArrowUp" -> "↑"
        "ArrowDown" -> "↓"
        "Space" -> "Space"
        "Comma" -> ","
        "Period" -> "."
        "Slash" -> "/"
        "Backslash" -> "\\"
        "Backquote" -> "`"
        "Equal" -> "="
        "Minus" -> "-"
        "BracketLeft" -> "["
        "BracketRight" -> "]"
        "Semicolon" -> ";"
        "Quote" -> "'"
        "PageUp" -> "Page Up"
        "PageDown" -> "Page Down"
        "NumpadAdd" -> "Num +"
        "NumpadSubtract" -> "Num -"
        "NumpadMultiply" -> "Num *"
        "NumpadDivide" -> "Num /"
        "NumpadDecimal" -> "Num ."
        else -> code
    }
}
