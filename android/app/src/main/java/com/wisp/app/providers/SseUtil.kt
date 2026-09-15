package com.wisp.app.providers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

/** Shared OkHttp client for every provider -- no read timeout, since a
 * streaming LLM response can legitimately take longer than any fixed
 * per-read timeout to finish emitting tokens. */
val sharedHttpClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(0, TimeUnit.MILLISECONDS)
    .build()

/**
 * Executes `request` and emits each `data: ...` line's payload from an
 * SSE (text/event-stream) response body as it arrives. Used by Anthropic,
 * OpenAI, OpenRouter and Gemini's streaming endpoints, which all speak
 * this same line format even though their JSON payload shapes differ --
 * callers pass `extractText` to pull the actual text delta out of each
 * event's JSON, and `null` from it means "not a content event, skip".
 *
 * Throws with the response body's error text on a non-2xx status, since
 * that's the only way the caller (ultimately the overlay UI) finds out
 * *why* a capture failed -- an auth error or rate limit here should reach
 * the user, not disappear as a silent empty response. Mirrors backend/
 * main.py's /capture error handling on the desktop side.
 */
fun streamSse(request: Request, extractText: (String) -> String?): Flow<String> = flow {
    val response: Response = sharedHttpClient.newCall(request).execute()
    response.use { resp ->
        if (!resp.isSuccessful) {
            val body = resp.body?.string().orEmpty()
            throw java.io.IOException("HTTP ${resp.code}: $body")
        }
        val source = resp.body?.source() ?: return@use
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith("data:")) continue
            val payload = line.removePrefix("data:").trim()
            if (payload == "[DONE]") break
            if (payload.isEmpty()) continue
            extractText(payload)?.let { emit(it) }
        }
    }
}
