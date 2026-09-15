package com.wisp.app.providers

import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class AnthropicProvider(private val model: String = "claude-sonnet-4-6") : LLMProvider {
    override fun streamVisionResponse(
        imageBase64: String,
        mimeType: String,
        transcriptContext: String,
        systemPrompt: String,
        apiKey: String,
    ): Flow<String> {
        val content = JSONArray()
            .put(
                JSONObject()
                    .put("type", "image")
                    .put(
                        "source",
                        JSONObject()
                            .put("type", "base64")
                            .put("media_type", mimeType)
                            .put("data", imageBase64),
                    ),
            )
            .put(JSONObject().put("type", "text").put("text", buildUserText(transcriptContext)))

        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 1024)
            .put("system", systemPrompt)
            .put("stream", true)
            .put(
                "messages",
                JSONArray().put(JSONObject().put("role", "user").put("content", content)),
            )

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return streamSse(request) { payload ->
            val json = JSONObject(payload)
            if (json.optString("type") != "content_block_delta") return@streamSse null
            val delta = json.optJSONObject("delta") ?: return@streamSse null
            if (delta.optString("type") != "text_delta") return@streamSse null
            delta.optString("text").takeIf { it.isNotEmpty() }
        }
    }
}
