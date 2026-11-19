package com.nuvio.app.features.player.desktop

import com.nuvio.app.core.storage.DesktopStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object WindowsMpvConfig {
    private const val ConfigKey = "config"
    private const val EnabledKey = "enabled"
    private val store by lazy { DesktopStorage.store("nuvio_windows_mpv") }
    private val state by lazy {
        val text = store.getString(ConfigKey).orEmpty()
        MutableStateFlow(Snapshot.fromStored(store.getBoolean(EnabledKey), text))
    }
    val settings by lazy { state.asStateFlow() }

    data class Snapshot(val enabled: Boolean, val text: String) {
        companion object {
            fun fromStored(enabled: Boolean?, text: String): Snapshot =
                Snapshot(enabled ?: text.isNotBlank(), text)
        }

        fun needsReopen(saved: Snapshot): Boolean {
            if (enabled != saved.enabled) return true
            if (!enabled || text == saved.text) return false
            val currentOptions = parse(text)
            val savedOptions = parse(saved.text)
            if (currentOptions.issues.isNotEmpty() || savedOptions.issues.isNotEmpty()) {
                return currentOptions.issues.isEmpty() != savedOptions.issues.isEmpty()
            }
            return !currentOptions.nativeOptions().contentEquals(savedOptions.nativeOptions())
        }
    }

    /**
     * Options that would detach mpv from the embedded window, take over input or the
     * window, load external code, or replace values the app derives per stream.
     * Everything else is passed to libmpv, which validates the values.
     */
    val blockedOptions = setOf(
        "wid", "vo", "gpu-context", "config", "config-dir", "include", "use-filedir-conf",
        "osc", "terminal", "idle", "keep-open", "force-window", "fs", "fullscreen", "geometry",
        "script", "scripts", "load-scripts", "ytdl", "ytdl-format", "ytdl-raw-options",
        "http-header-fields", "user-agent", "referrer", "cookies", "cookies-file",
        "audio-file", "audio-files", "start", "pause", "playlist", "playlist-start",
        "resume-playback", "save-position-on-quit",
    )
    private val blockedPrefixes = listOf("input-", "window-", "watch-later-")

    /** List option suffixes mpv accepts on the command line, e.g. `glsl-shaders-append`. */
    private val listActionSuffixes = listOf("-add", "-append", "-pre", "-set", "-remove", "-clr", "-toggle")

    fun baseOptionName(name: String): String {
        if (name == "glsl-shader") return "glsl-shaders"
        val suffix = listActionSuffixes.firstOrNull { name.endsWith(it) && name.length > it.length }
        return if (suffix == null) name else name.dropLast(suffix.length)
    }

    fun isBlocked(name: String): Boolean {
        val base = baseOptionName(name)
        return base in blockedOptions || blockedPrefixes.any(base::startsWith)
    }

    data class Option(val line: Int, val name: String, val value: String)

    enum class ErrorKind { Syntax, Unsupported, InvalidValue, ReservedLabel, ProfileIgnored }

    data class Issue(val kind: ErrorKind, val line: Int? = null, val detail: String = "")

    /** [warnings] do not block saving or playback; they mark lines that were skipped. */
    data class Parsed(val options: List<Option>, val issues: List<Issue>, val warnings: List<Issue> = emptyList()) {
        fun nativeOptions(): Array<String> = options.flatMap { listOf(it.name, it.value) }.toTypedArray()
    }

    fun load(): String = settings.value.text

    @Synchronized
    fun save(text: String) {
        val current = state.value
        store.putBoolean(EnabledKey, current.enabled)
        store.putString(ConfigKey, text.takeUnless(String::isBlank))
        state.value = current.copy(text = text.takeUnless(String::isBlank).orEmpty())
    }

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        val current = state.value
        store.putBoolean(EnabledKey, enabled)
        state.value = current.copy(enabled = enabled)
    }

    fun parse(text: String): Parsed {
        val options = mutableListOf<Option>()
        val issues = mutableListOf<Issue>()
        val warnings = mutableListOf<Issue>()
        var inProfile = false
        text.removePrefix("\uFEFF").lineSequence().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith('#')) return@forEachIndexed

            val number = index + 1
            if ('\u0000' in line) {
                issues += Issue(ErrorKind.Syntax, number)
                return@forEachIndexed
            }
            // mpv.conf profile sections: only [default] (top level) is applied.
            if (line.startsWith('[')) {
                val end = line.indexOf(']')
                if (end <= 1 || !isCommentOrBlank(line.substring(end + 1))) {
                    issues += Issue(ErrorKind.Syntax, number)
                    return@forEachIndexed
                }
                val profile = line.substring(1, end).trim()
                inProfile = profile != "default"
                if (inProfile) warnings += Issue(ErrorKind.ProfileIgnored, number, profile)
                return@forEachIndexed
            }
            if (inProfile) return@forEachIndexed

            val body = line.removePrefix("--")
            // A `#` before the first `=` starts a comment after a bare flag.
            val separator = body.indexOf('=').takeUnless { equals ->
                val comment = body.indexOf('#')
                comment in 0 until equals
            } ?: -1
            val rawName = (if (separator < 0) body.substringBefore('#') else body.substring(0, separator)).trim()
            if (rawName.isEmpty() || rawName.any { it.isWhitespace() || it == '"' || it == '\'' }) {
                issues += Issue(ErrorKind.Syntax, number)
                return@forEachIndexed
            }
            val (name, value) = if (separator < 0) {
                // Bare flags: `deband` sets yes, `no-deband` sets no.
                if (rawName.startsWith("no-") && rawName.length > 3) rawName.substring(3) to "no" else rawName to "yes"
            } else {
                rawName to (parseValue(body.substring(separator + 1)) ?: run {
                    issues += Issue(ErrorKind.Syntax, number)
                    return@forEachIndexed
                })
            }
            if (isBlocked(name)) {
                issues += Issue(ErrorKind.Unsupported, number, name)
                return@forEachIndexed
            }
            options += Option(number, name, value)
        }
        return Parsed(options, issues, warnings)
    }

    private fun isCommentOrBlank(text: String): Boolean = text.trim().let { it.isEmpty() || it.startsWith('#') }

    /** Returns the value with quotes removed and any trailing `# comment` stripped, or null on bad quoting. */
    private fun parseValue(text: String): String? {
        val value = text.trim()
        val quote = value.firstOrNull()
        if (quote == '"' || quote == '\'') {
            val close = value.indexOf(quote, startIndex = 1)
            if (close < 0 || !isCommentOrBlank(value.substring(close + 1))) return null
            return value.substring(1, close)
        }
        return value.substringBefore('#').trim()
    }

    fun validate(text: String): List<Issue> {
        val parsed = parse(text)
        if (parsed.issues.isNotEmpty() || parsed.options.isEmpty()) return parsed.issues
        NativePlayerBridge.ensureLoaded()
        return validationIssues(parsed, NativePlayerBridge.validateWindowsMpvOptions(parsed.nativeOptions()))
    }

    fun validationIssues(parsed: Parsed, nativeErrors: Array<String?>): List<Issue> {
        check(nativeErrors.size == parsed.options.size + 2)
        return buildList {
            parsed.options.forEachIndexed { index, option ->
                nativeErrors[index]?.let { add(Issue(ErrorKind.InvalidValue, option.line, it)) }
            }
            nativeErrors.drop(parsed.options.size).filterNotNull().forEach {
                add(Issue(ErrorKind.ReservedLabel, detail = it))
            }
        }
    }
}
