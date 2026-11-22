package com.nuvio.app.core.network

import com.nuvio.app.core.diagnostics.DebugLog
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.plugin

internal actual fun createApiHttpClient(): HttpClient = HttpClient(CIO) {
    followRedirects = false
    expectSuccess = false
    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 30_000
    }
}.also { client ->
    client.plugin(HttpSend).intercept { request ->
        if (!DebugLog.isEnabled) return@intercept execute(request)
        val started = System.nanoTime()
        val method = request.method.value
        val url = request.url.buildString()
        try {
            execute(request).also { call ->
                DebugLog.request(method, url, call.response.status.value, (System.nanoTime() - started) / 1_000_000)
            }
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            DebugLog.request(method, url, null, (System.nanoTime() - started) / 1_000_000, error)
            throw error
        }
    }
}
