package com.wisp.app.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * BYOK persistence, Android-side. Keys are stored in an
 * EncryptedSharedPreferences file backed by the Android Keystore -- a
 * strict improvement over the desktop app's plaintext
 * `userData/settings.json` (see src/main/settings.ts's own doc comment
 * acknowledging that gap). Same posture otherwise: local-only, never
 * synced anywhere, read only by this app.
 */
class SettingsStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "wisp_settings",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun load(): WispSettings {
        val provider = Provider.fromId(prefs.getString(KEY_PROVIDER, Provider.ANTHROPIC.id)!!)
        val apiKeys = Provider.entries.associateWith { prefs.getString(keyFor(it), "") ?: "" }
        val ollamaUrl = prefs.getString(KEY_OLLAMA_URL, WispSettings().ollamaBaseUrl)!!
        return WispSettings(provider = provider, apiKeys = apiKeys, ollamaBaseUrl = ollamaUrl)
    }

    fun save(settings: WispSettings) {
        prefs.edit().apply {
            putString(KEY_PROVIDER, settings.provider.id)
            for ((provider, key) in settings.apiKeys) {
                putString(keyFor(provider), key)
            }
            putString(KEY_OLLAMA_URL, settings.ollamaBaseUrl)
        }.apply()
    }

    private fun keyFor(provider: Provider) = "api_key_${provider.id}"

    companion object {
        private const val KEY_PROVIDER = "provider"
        private const val KEY_OLLAMA_URL = "ollama_base_url"
    }
}
