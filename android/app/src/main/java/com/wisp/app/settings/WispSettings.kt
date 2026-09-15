package com.wisp.app.settings

/** Mirrors the desktop app's WispSettings shape (src/main/settings.ts) so the
 * two apps' BYOK model stays conceptually identical even though storage
 * differs (EncryptedSharedPreferences here vs. a plaintext userData JSON
 * file there -- see SettingsStore's doc comment for why). */
enum class Provider(val id: String, val label: String, val needsKey: Boolean) {
    ANTHROPIC("anthropic", "Anthropic (Claude)", true),
    OPENAI("openai", "OpenAI (GPT-4o)", true),
    GEMINI("gemini", "Google (Gemini)", true),
    OPENROUTER("openrouter", "OpenRouter (many models, one key)", true),
    OLLAMA("ollama", "Ollama (remote server, private)", false);

    companion object {
        fun fromId(id: String): Provider = entries.firstOrNull { it.id == id } ?: ANTHROPIC
    }
}

data class WispSettings(
    val provider: Provider = Provider.ANTHROPIC,
    val apiKeys: Map<Provider, String> = emptyMap(),
    // Unlike the desktop app (where Ollama runs on the same machine at
    // 127.0.0.1), a phone has no "local" Ollama of its own -- this must
    // point at Ollama running on a reachable machine on the same network.
    val ollamaBaseUrl: String = "http://192.168.1.100:11434",
) {
    fun apiKeyFor(p: Provider): String = apiKeys[p] ?: ""

    fun hasKeyConfigured(): Boolean =
        provider == Provider.OLLAMA || apiKeyFor(provider).isNotBlank()
}
