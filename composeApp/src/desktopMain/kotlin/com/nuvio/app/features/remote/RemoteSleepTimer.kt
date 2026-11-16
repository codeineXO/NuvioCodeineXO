package com.nuvio.app.features.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * The phone's sleep timer. It runs here, not on the phone, so it keeps counting when the phone locks. When it ends
 * it pauses what is playing and, if asked, puts the PC to sleep. It cancels itself when the player closes.
 */
internal object RemoteSleepTimer {
    /** When it ends (null: when the episode does) and whether it then puts the PC to sleep. */
    private data class Timer(val endsAtMs: Long?, val sleepPc: Boolean)

    private const val MAX_SECONDS = 240 * 60
    private const val EPISODE_END_MARGIN_MS = 5_000L
    private const val PAUSE_THEN_SLEEP_MS = 2_000L
    private const val CLOSED_GRACE_MS = 5_000L

    private val timer = MutableStateFlow<Timer?>(null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    /** `{"k":"sleep","active":…,"endOfEpisode":…,"remainingMs":…,"sleepPc":…}`, on every change. */
    val messages: Flow<String> get() = timer.map { running ->
        buildJsonObject {
            put("k", "sleep")
            put("active", running != null)
            put("endOfEpisode", running != null && running.endsAtMs == null)
            put("remainingMs", running?.endsAtMs?.let { (it - System.currentTimeMillis()).coerceAtLeast(0) })
            put("sleepPc", running?.sleepPc == true)
        }.toString()
    }.distinctUntilChanged()

    /** `sleepSet` (`seconds`, or `endOfEpisode`, and `sleepPc`) or `sleepCancel`. */
    fun handle(cmd: String, json: JsonObject) {
        when (cmd) {
            "sleepCancel" -> cancel()
            "sleepSet" -> {
                val seconds = json["seconds"]?.jsonPrimitive?.intOrNull
                val atEnd = json["endOfEpisode"]?.jsonPrimitive?.booleanOrNull == true
                if (seconds != null && seconds !in 1..MAX_SECONDS) return
                if (seconds == null && !atEnd) return
                start(seconds, json["sleepPc"]?.jsonPrimitive?.booleanOrNull == true)
            }
        }
    }

    @Synchronized
    private fun start(seconds: Int?, sleepPc: Boolean) {
        // Only while something is open in the player.
        if (RemotePlayerLink.state.value == null) return
        val next = Timer(seconds?.let { System.currentTimeMillis() + it * 1_000L }, sleepPc)
        job?.cancel()
        timer.value = next
        job = scope.launch {
            val closed = launch {
                waitUntilPlayerCloses()
                cancel()
            }
            if (next.endsAtMs != null) {
                delay((next.endsAtMs - System.currentTimeMillis()).coerceAtLeast(0))
            } else {
                waitForEpisodeEnd()
            }
            closed.cancel()
            finish(next)
        }
    }

    @Synchronized
    private fun cancel() {
        job?.cancel()
        job = null
        timer.value = null
    }

    @Synchronized
    private fun end(done: Timer) {
        if (timer.value == done) {
            job = null
            timer.value = null
        }
    }

    private suspend fun finish(done: Timer) {
        // Pause only what is playing, so the timer never starts anything.
        if (RemotePlayerLink.state.value?.isPlaying == true) RemotePlayerLink.send("toggle", 0.0)
        end(done)
        if (done.sleepPc) {
            delay(PAUSE_THEN_SLEEP_MS)
            sleepPc()
        }
    }

    /** A few seconds of grace, so switching episodes inside the player doesn't cancel the timer. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun waitUntilPlayerCloses() {
        RemotePlayerLink.state.mapLatest { open ->
            if (open == null) {
                delay(CLOSED_GRACE_MS)
                true
            } else false
        }.first { it }
    }

    /** Until the last few seconds of the episode, so the next one doesn't start by itself. */
    private suspend fun waitForEpisodeEnd() {
        while (true) {
            val (position, duration) = RemotePlayerLink.progress() ?: (0L to 0L)
            if (duration > 0 && duration - position <= EPISODE_END_MARGIN_MS) return
            delay(1_000)
        }
    }

    /** Sleep, not hibernate or shut down; no admin rights are needed for any of these. */
    private fun sleepPc() {
        val os = System.getProperty("os.name").lowercase()
        val command = when {
            "win" in os -> listOf(
                "powershell", "-NoProfile", "-NonInteractive", "-Command",
                "Add-Type -AssemblyName System.Windows.Forms; " +
                    "[System.Windows.Forms.Application]::SetSuspendState('Suspend', \$false, \$false)",
            )
            "mac" in os -> listOf("pmset", "sleepnow")
            else -> listOf("systemctl", "suspend")
        }
        // If the system refuses (e.g. a work-managed PC), the timer has still paused playback.
        runCatching {
            ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        }
    }
}
