package com.wisp.app.providers

import kotlinx.coroutines.flow.Flow

/**
 * Kotlin mirror of the desktop app's backend/providers/base.py -- same
 * shape (image + rolling transcript + system prompt in, streamed text
 * chunks out), different transport: the desktop backend calls provider
 * SDKs from Python; here the app calls each provider's HTTP API directly
 * with OkHttp, since there's no local backend process on mobile (client-
 * only architecture, see docs/progress.md session 6).
 */
interface LLMProvider {
    /**
     * @param imageBase64 raw base64 (no "data:image/png;base64," prefix --
     *   callers strip that, unlike the desktop app's data-URL convention)
     * @param mimeType e.g. "image/jpeg"
     */
    fun streamVisionResponse(
        imageBase64: String,
        mimeType: String,
        transcriptContext: String,
        systemPrompt: String,
        apiKey: String,
    ): Flow<String>
}

fun buildUserText(transcriptContext: String): String =
    if (transcriptContext.isNotBlank()) {
        "Recent conversation transcript:\n$transcriptContext\n\n" +
            "Here is the current screen. Respond with the most useful, concise assistance for what's happening right now."
    } else {
        "Here is the current screen. Respond with the most useful, concise assistance for what's happening right now."
    }
