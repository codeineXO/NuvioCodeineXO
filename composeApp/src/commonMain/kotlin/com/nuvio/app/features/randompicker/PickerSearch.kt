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
    for (retry in 0..2) {
        currentCoroutineContext().ensureActive()
        val outcome = try { attempt(retry) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { PickerOutcome(failedSources = 1) }
        currentCoroutineContext().ensureActive()
        if (outcome.result != null || outcome.noNewTitles || retry == 2 ||
            outcome.pageRequests == 0 && outcome.metadataAttempts == 0 && outcome.failedSources == 0 && !outcome.timedOut) return outcome
        onRetry(outcome)
        delay((retry.toLong() + 1).coerceAtMost(5) * 1_000)
    }
    error("Shuffle retry loop did not return")
}
