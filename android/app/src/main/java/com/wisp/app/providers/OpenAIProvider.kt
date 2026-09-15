package com.wisp.app.providers

import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Also the base for OpenRouterProvider, which is OpenAI-API-compatible and
 * differs only in base URL, model default, and header name for the key --
 * same relationship as the desktop app's openrouter_provider.py reusing
 * the `openai` Python SDK instead of writing a second HTTP client.
 */
open class OpenAIProvider(
    private val model: String = "gpt-4o",
    private val baseUrl: String = "https://api.openai.com/v1",
) : LLMProvider {
    override fun streamVisionResponse(
        imageBase64: String,
        mimeType: String,
        transcriptContext: String,
        systemPrompt: String,
        apiKey: String,
    ): Flow<String> {
        val userContent = JSONArray()
            .put(JSONObject().put("type", "text").put("text", buildUserText(transcriptContext)))
            .put(
                JSONObject()
                    .put("type", "image_url")
                    .put(
                        "image_url",
                        JSONObject().put("url", "data:$mimeType;base64,$imageBase64"),
                    ),
            )

        val body = JSONObject()
            .put("model", model)
            .put("stream", true)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(JSONObject().put("role", "user").put("content", userContent)),
            )

        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return streamSse(request) { payload ->
            val choices = JSONObject(payload).optJSONArray("choices") ?: return@streamSse null
            if (choices.length() == 0) return@streamSse null
            val delta = choices.getJSONObject(0).optJSONObject("delta") ?: return@streamSse null
            delta.optString("content").takeIf { it.isNotEmpty() }
        }
    }
}
