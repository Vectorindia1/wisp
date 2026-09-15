package com.wisp.app.providers

/** One key, many underlying models via OpenRouter's OpenAI-compatible API.
 * See OpenAIProvider's doc comment -- same relationship as the desktop
 * app's openrouter_provider.py. */
class OpenRouterProvider(model: String = "openai/gpt-4o-mini") :
    OpenAIProvider(model = model, baseUrl = "https://openrouter.ai/api/v1")
