package com.nuvio.app.core.network

import com.nuvio.app.core.diagnostics.DebugLog
import okhttp3.Interceptor
import okhttp3.Response

/** Traces every OkHttp request (method, URL, status, timing) while debug logging is on. */
internal object DebugLogHttpInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!DebugLog.isEnabled) return chain.proceed(request)
        val started = System.nanoTime()
        return try {
            chain.proceed(request).also { response ->
                val cached = if (response.networkResponse == null && response.cacheResponse != null) " [cache]" else ""
                DebugLog.request(
                    method = request.method,
                    url = request.url.toString() + cached,
                    status = response.code,
                    durationMs = (System.nanoTime() - started) / 1_000_000,
                )
            }
        } catch (error: Exception) {
            // A cancelled call (screen left, coroutine cancelled) is not a failure.
            if (chain.call().isCanceled()) throw error
            DebugLog.request(
                method = request.method,
                url = request.url.toString(),
                status = null,
                durationMs = (System.nanoTime() - started) / 1_000_000,
                error = error,
            )
            throw error
        }
    }
}
