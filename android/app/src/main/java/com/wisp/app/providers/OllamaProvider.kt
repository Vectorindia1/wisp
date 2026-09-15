package com.wisp.app.providers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Unlike every other provider here, points at a server the user runs
 * themselves rather than a cloud API -- and unlike the desktop app (where
 * Ollama is typically localhost), a phone has no "local" Ollama, so
 * baseUrl must be a reachable address on the same network (see
 * WispSettings.ollamaBaseUrl's default and doc comment).
 *
 * Ollama's /api/generate streams newline-delimited JSON objects, NOT
 * text/event-stream ("data: " lines) like the other 4 providers, so this
 * can't reuse streamSse() -- same distinction as the desktop app's
 * ollama_provider.py vs. its SSE-based siblings.
 */
class OllamaProvider(private val baseUrl: String, private val model: String = "llava") : LLMProvider {
    override fun streamVisionResponse(
        imageBase64: String,
        mimeType: String,
        transcriptContext: String,
        systemPrompt: String,
        apiKey: String,
    ): Flow<String> = flow {
        val prompt = "$systemPrompt\n\n${buildUserText(transcriptContext)}"
        val body = JSONObject()
            .put("model", model)
            .put("prompt", prompt)
            .put("images", JSONArray().put(imageBase64))
            .put("stream", true)

        val request = Request.Builder()
            .url("$baseUrl/api/generate")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = sharedHttpClient.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("HTTP ${resp.code}: ${resp.body?.string().orEmpty()}")
            }
            val source = resp.body?.source() ?: return@use
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank()) continue
                val json = JSONObject(line)
                json.optString("response").takeIf { it.isNotEmpty() }?.let { emit(it) }
                if (json.optBoolean("done", false)) break
            }
        }
    }
}
