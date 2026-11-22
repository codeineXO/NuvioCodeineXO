package com.nuvio.app.core.diagnostics

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Opt-in debug logging. The platform installs the file writer and supplies persistence and
 * the log folder through [initialize]; until then the toggle is unsupported and hidden.
 */
object DebugLogSettings {
    private val mutableEnabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = mutableEnabled.asStateFlow()

    private val mutableRedactSecrets = MutableStateFlow(true)
    val redactSecrets: StateFlow<Boolean> = mutableRedactSecrets.asStateFlow()

    var isSupported: Boolean = false
        private set
    var logFolderPath: String = ""
        private set

    private var saveEnabled: (Boolean) -> Unit = {}
    private var saveRedactSecrets: (Boolean) -> Unit = {}
    private var openFolder: () -> Unit = {}

    fun initialize(
        enabled: Boolean,
        redactSecrets: Boolean,
        logFolderPath: String,
        saveEnabled: (Boolean) -> Unit,
        saveRedactSecrets: (Boolean) -> Unit,
        openLogFolder: () -> Unit,
    ) {
        mutableEnabled.value = enabled
        mutableRedactSecrets.value = redactSecrets
        this.logFolderPath = logFolderPath
        this.saveEnabled = saveEnabled
        this.saveRedactSecrets = saveRedactSecrets
        this.openFolder = openLogFolder
        isSupported = true
    }

    fun setEnabled(enabled: Boolean) {
        if (mutableEnabled.value == enabled) return
        saveEnabled(enabled)
        mutableEnabled.value = enabled
    }

    fun toggle(): Boolean {
        val next = !mutableEnabled.value
        setEnabled(next)
        return next
    }

    fun setRedactSecrets(redact: Boolean) {
        if (mutableRedactSecrets.value == redact) return
        saveRedactSecrets(redact)
        mutableRedactSecrets.value = redact
    }

    fun openLogFolder() = openFolder()
}

/** Breadcrumbs and traces that only cost anything while debug logging is on. */
object DebugLog {
    private val ui = Logger.withTag("UI")
    private val net = Logger.withTag("Net")
    private val settings = Logger.withTag("Settings")

    val isEnabled: Boolean get() = DebugLogSettings.enabled.value

    fun navigation(message: String) {
        if (isEnabled) ui.i { message }
    }

    fun settingChanged(store: String, key: String, value: String?) {
        if (!isEnabled) return
        val shown = when {
            value == null -> "<removed>"
            value.length > 120 -> "<${value.length} chars>"
            else -> value
        }
        settings.i { "$store.$key=$shown" }
    }

    fun request(method: String, url: String, status: Int?, durationMs: Long, error: Throwable? = null) {
        if (!isEnabled) return
        val outcome = when {
            error != null -> "FAILED ${error::class.simpleName}: ${error.message}"
            status != null -> status.toString()
            else -> "?"
        }
        if (error != null || (status != null && status >= 400)) {
            net.w { "$method $url -> $outcome (${durationMs}ms)" }
        } else {
            net.d { "$method $url -> $outcome (${durationMs}ms)" }
        }
    }
}

/** Masks credentials in log lines while keeping hosts and paths readable. */
object LogRedactor {
    private const val MASK = "***"

    private val secretName =
        "(?:api[_-]?key|apikey|access[_-]?token|refresh[_-]?token|id[_-]?token|token|auth|authorization|" +
            "password|passwd|pass|secret|client[_-]?secret|client[_-]?id|session|sessionid|sid|signature|sig|key|code|cookie|" +
            "x-plex-token|x-emby-token|x-mediabrowser-token|rd[_-]?key|debrid[_-]?key)"

    private val queryParam = Regex("(?i)([?&;]$secretName=)([^&#\\s\"']+)")
    private val headerLike = Regex("(?i)\\b((?:authorization|proxy-authorization|cookie|set-cookie|x-[a-z-]*token|x-api-key)\\s*[:=]\\s*)([^\\r\\n,;]+)")
    private val bearer = Regex("(?i)\\b(bearer|basic)\\s+[A-Za-z0-9._~+/=-]{8,}")
    private val jsonField = Regex("(?i)(\"$secretName\"\\s*:\\s*\")([^\"]+)(\")")
    private val keyValue = Regex("(?i)\\b([a-z0-9_.-]*(?:token|secret|password|apikey|api_key)[a-z0-9_.-]*=)(\\S+)")
    private val userInfo = Regex("(?i)(\\b[a-z][a-z0-9+.-]*://)([^/@\\s:]+):([^/@\\s]+)@")
    private val jwt = Regex("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}")

    // Path segments that look like a credential: long opaque tokens (debrid/addon config blobs,
    // API keys embedded in stremio addon URLs). Hex/base64ish runs of 24+ chars.
    private val opaquePathSegment = Regex("(?<=/)[A-Za-z0-9_%=+-]{24,}(?=[/?#\\s\"']|$)")

    // Shorter per-user ids in addon paths, e.g. /usr_93bdff379f561914/ or /u/p_2bwcebc6l9w9/.
    private val userIdPathSegment = Regex("(?<=/)[A-Za-z0-9_-]{12,23}(?=/)")
    private val readableIdSegment = Regex("tt\\d+|\\d+|s\\d+e\\d+")
    private val encodedReadableChar = Regex("(?i)%(20|5B|5D|26|28|29|2C)")

    fun redact(line: String): String {
        var out = line
        out = userInfo.replace(out) { "${it.groupValues[1]}$MASK:$MASK@" }
        out = bearer.replace(out) { "${it.groupValues[1]} $MASK" }
        out = headerLike.replace(out) { "${it.groupValues[1]}$MASK" }
        out = jsonField.replace(out) { "${it.groupValues[1]}$MASK${it.groupValues[3]}" }
        out = queryParam.replace(out) { "${it.groupValues[1]}$MASK" }
        out = keyValue.replace(out) { "${it.groupValues[1]}$MASK" }
        out = jwt.replace(out, MASK)
        out = opaquePathSegment.replace(out) { match ->
            val segment = match.value
            // Keep readable names (words, ids with few digit/letter switches); mask token-like blobs.
            if (looksOpaque(segment)) "$MASK${segment.length}" else segment
        }
        out = userIdPathSegment.replace(out) { match ->
            if (looksLikeUserId(match.value)) "$MASK${match.value.length}" else match.value
        }
        return out
    }

    private fun looksLikeUserId(segment: String): Boolean {
        val digits = segment.count { it.isDigit() }
        val letters = segment.count { it.isLetter() }
        // IMDb/TMDB ids (tt1234567, 95479) and episode tags (s01e09) are left readable.
        if (readableIdSegment.matches(segment)) return false
        return digits >= 4 && letters >= 3
    }

    private fun looksOpaque(segment: String): Boolean {
        // URL-encoded spaces/brackets mean readable names (folders, file titles), not keys.
        if (encodedReadableChar.containsMatchIn(segment)) return false
        val digits = segment.count { it.isDigit() }
        val upper = segment.count { it.isUpperCase() }
        val lower = segment.count { it.isLowerCase() }
        val classes = listOf(digits, upper, lower).count { it > 0 }
        // Base64/JSON-config blobs and hex keys mix character classes heavily.
        return classes >= 2 && digits >= 3 || segment.length >= 40
    }
}
