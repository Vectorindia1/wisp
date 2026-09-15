package com.wisp.app.providers

import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class GeminiProvider(private val model: String = "gemini-1.5-flash") : LLMProvider {
    override fun streamVisionResponse(
        imageBase64: String,
        mimeType: String,
        transcriptContext: String,
        systemPrompt: String,
        apiKey: String,
    ): Flow<String> {
        val parts = JSONArray()
            .put(
                JSONObject().put(
                    "inline_data",
                    JSONObject().put("mime_type", mimeType).put("data", imageBase64),
                ),
            )
            .put(JSONObject().put("text", buildUserText(transcriptContext)))

        val body = JSONObject()
            .put(
                "system_instruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))),
            )
            .put(
                "contents",
                JSONArray().put(JSONObject().put("parts", parts)),
            )

        // ?alt=sse switches Gemini's streaming endpoint from a single JSON
        // array response to a proper text/event-stream, letting this reuse
        // the same streamSse() line-reader as every other provider here.
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent" +
            "?alt=sse&key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return streamSse(request) { payload ->
            val candidates = JSONObject(payload).optJSONArray("candidates") ?: return@streamSse null
            if (candidates.length() == 0) return@streamSse null
            val content = candidates.getJSONObject(0).optJSONObject("content") ?: return@streamSse null
            val partsOut = content.optJSONArray("parts") ?: return@streamSse null
            if (partsOut.length() == 0) return@streamSse null
            partsOut.getJSONObject(0).optString("text").takeIf { it.isNotEmpty() }
        }
    }
}
