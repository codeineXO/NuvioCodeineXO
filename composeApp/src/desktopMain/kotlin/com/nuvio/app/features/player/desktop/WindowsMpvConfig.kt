package com.nuvio.app.features.player.desktop

import com.nuvio.app.core.storage.DesktopStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object WindowsMpvConfig {
    private const val ConfigKey = "config"
    private const val EnabledKey = "enabled"
    private const val LoggingEnabledKey = "logging_enabled"
    private val store by lazy { DesktopStorage.store("nuvio_windows_mpv") }
    private val state by lazy {
        val text = store.getString(ConfigKey).orEmpty()
        MutableStateFlow(Snapshot.fromStored(store.getBoolean(EnabledKey), text).copy(
            loggingEnabled = store.getBoolean(LoggingEnabledKey) ?: false,
            hlsSegmentRetryEnabled = store.getBoolean("hls_segment_retry_enabled") ?: false,
        ))
    }
    val settings by lazy { state.asStateFlow() }

    data class Snapshot(
        val enabled: Boolean,
        val text: String,
        val loggingEnabled: Boolean = false,
        val hlsSegmentRetryEnabled: Boolean = false,
    ) {
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

    val supportedOptions = setOf(
        "scale", "cscale", "dscale", "deband", "dither-depth", "tone-mapping",
        "video-sync", "interpolation",
        "glsl-shader", "glsl-shaders", "glsl-shaders-append", "glsl-shader-opts",
        "vf", "vf-add", "af", "af-add",
    )

    data class Option(val line: Int, val name: String, val value: String)

    enum class ErrorKind { Syntax, Unsupported, InvalidValue, ReservedLabel }

    data class Issue(val kind: ErrorKind, val line: Int? = null, val detail: String = "")

    data class Parsed(val options: List<Option>, val issues: List<Issue>) {
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

    @Synchronized
    fun setLoggingEnabled(enabled: Boolean) {
        store.putBoolean(LoggingEnabledKey, enabled)
        state.value = state.value.copy(loggingEnabled = enabled)
    }

    @Synchronized
    fun setHlsSegmentRetryEnabled(enabled: Boolean) {
        store.putBoolean("hls_segment_retry_enabled", enabled)
        state.value = state.value.copy(hlsSegmentRetryEnabled = enabled)
    }

    fun parse(text: String): Parsed {
        val options = mutableListOf<Option>()
        val issues = mutableListOf<Issue>()
        text.removePrefix("\uFEFF").lineSequence().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith('#')) return@forEachIndexed

            val number = index + 1
            val separator = line.indexOf('=')
            if (separator <= 0 || '\u0000' in line) {
                issues += Issue(ErrorKind.Syntax, number)
                return@forEachIndexed
            }
            val name = line.substring(0, separator).trim()
            if (name !in supportedOptions) {
                issues += Issue(ErrorKind.Unsupported, number, name)
                return@forEachIndexed
            }

            val value = line.substring(separator + 1).trim()
            val quote = value.firstOrNull()
            if ((quote == '"' || quote == '\'') && (value.length < 2 || value.last() != quote)) {
                issues += Issue(ErrorKind.Syntax, number)
                return@forEachIndexed
            }
            val unquoted = if (quote == '"' || quote == '\'') value.substring(1, value.lastIndex) else value
            options += Option(number, name, unquoted)
        }
        return Parsed(options, issues)
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
