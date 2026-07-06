package de.bgg_home.texdroid.ai

import android.content.Context

/**
 * Einstellungen der KI-Assistenz (multi-provider, BYOK). Flags/Provider/Modelle
 * liegen in normalen SharedPreferences; die **API-Keys** verschlüsselt über
 * [SecureKeyStore] — **je Provider** ein eigener Key, damit man ohne
 * Neu-Eintippen wechseln kann. Feature per Default **deaktiviert** (Opt-in).
 */
class AiSettings(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("ai_settings", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var provider: AiProvider
        get() = AiProvider.fromId(prefs.getString(KEY_PROVIDER, null))
        set(value) = prefs.edit().putString(KEY_PROVIDER, value.id).apply()

    fun keyFor(p: AiProvider): String =
        prefs.getString(keyName(p), null)
            ?.let { runCatching { SecureKeyStore.decrypt(it) }.getOrDefault("") }
            ?: ""

    fun setKeyFor(p: AiProvider, value: String) = prefs.edit()
        .putString(keyName(p), if (value.isBlank()) null else SecureKeyStore.encrypt(value))
        .apply()

    fun modelFor(p: AiProvider): String =
        prefs.getString(modelName(p), null)?.takeIf { it.isNotBlank() } ?: p.defaultModel

    fun setModelFor(p: AiProvider, value: String) = prefs.edit()
        .putString(modelName(p), value.ifBlank { p.defaultModel })
        .apply()

    /** Key/Modell des aktuell gewählten Providers. */
    val activeKey: String get() = keyFor(provider)
    val activeModel: String get() = modelFor(provider)

    /** true, wenn aktiv UND für den gewählten Provider ein Key hinterlegt ist. */
    val isReady: Boolean get() = enabled && activeKey.isNotBlank()

    private fun keyName(p: AiProvider) = "api_key_enc_${p.id}"
    private fun modelName(p: AiProvider) = "model_${p.id}"

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_PROVIDER = "provider"
    }
}
