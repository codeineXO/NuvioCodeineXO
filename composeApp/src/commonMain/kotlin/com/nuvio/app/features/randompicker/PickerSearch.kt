package com.nuvio.app.features.randompicker

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** Bounded attempts with a pause between them; user cancellation remains authoritative. */
internal suspend fun searchUntilResult(
    attempt: suspend (Int) -> PickerOutcome,
    onRetry: (PickerOutcome) -> Unit,
): PickerOutcome {
    var retry = 0
    while (true) {
        currentCoroutineContext().ensureActive()
        val outcome = try { attempt(retry) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { PickerOutcome(failedSources = 1) }
        currentCoroutineContext().ensureActive()
        if (outcome.result != null) return outcome
        onRetry(outcome)
        delay((retry.toLong() + 1).coerceAtMost(5) * 1_000)
        if (retry < Int.MAX_VALUE) retry++
    }
}
