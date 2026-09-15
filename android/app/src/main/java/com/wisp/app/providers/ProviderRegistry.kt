package com.wisp.app.providers

import com.wisp.app.settings.Provider
import com.wisp.app.settings.WispSettings

fun getProvider(settings: WispSettings): LLMProvider = when (settings.provider) {
    Provider.ANTHROPIC -> AnthropicProvider()
    Provider.OPENAI -> OpenAIProvider()
    Provider.GEMINI -> GeminiProvider()
    Provider.OPENROUTER -> OpenRouterProvider()
    Provider.OLLAMA -> OllamaProvider(baseUrl = settings.ollamaBaseUrl)
}
