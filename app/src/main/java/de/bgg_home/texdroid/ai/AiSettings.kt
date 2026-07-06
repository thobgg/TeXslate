package de.bgg_home.texdroid.ai

import android.content.Context

/** Verfügbare Modelle für die KI-Assistenz (Anzeigename → API-Modell-ID). */
val AI_MODELS = listOf(
    "Sonnet 5" to "claude-sonnet-5",
    "Opus 4.8" to "claude-opus-4-8",
    "Haiku 4.5" to "claude-haiku-4-5-20251001",
)

const val DEFAULT_AI_MODEL = "claude-sonnet-5"

/**
 * Einstellungen der KI-Assistenz. Flags/Modell liegen in normalen SharedPreferences,
 * der **API-Key** verschlüsselt über [SecureKeyStore]. Feature ist per Default
 * **deaktiviert** (Opt-in).
 */
class AiSettings(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("ai_settings", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, DEFAULT_AI_MODEL) ?: DEFAULT_AI_MODEL
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_ENC, null)
            ?.let { runCatching { SecureKeyStore.decrypt(it) }.getOrDefault("") }
            ?: ""
        set(value) = prefs.edit()
            .putString(KEY_API_ENC, if (value.isBlank()) null else SecureKeyStore.encrypt(value))
            .apply()

    /** true, wenn Feature aktiv UND ein Key hinterlegt ist. */
    val isReady: Boolean get() = enabled && apiKey.isNotBlank()

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_MODEL = "model"
        const val KEY_API_ENC = "api_key_enc"
    }
}
