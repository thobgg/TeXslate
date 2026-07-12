package de.bgg_home.texdroid.ai

import android.content.Context

/**
 * Heuristik: Sieht [s] nach einem API-Key aus (statt nach einem Modellnamen)?
 * Modellnamen sind kurz (`claude-sonnet-5`, `gpt-4o`); Keys tragen ein
 * Provider-Präfix (`sk-…` Anthropic/OpenAI, `AIza…` Google) oder sind sehr lang.
 * Schützt davor, dass ein versehentlich ins Modell-Feld getippter Key als
 * **unverschlüsselter** Klartext-Modellname gespeichert wird.
 */
fun looksLikeApiKey(s: String): Boolean {
    val t = s.trim()
    return t.startsWith("sk-") || t.startsWith("AIza") || t.length > 64
}

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
        .putString(
            modelName(p),
            // Sicherheitsnetz: einen (unverschlüsselten) Modellnamen, der wie ein
            // API-Key aussieht, niemals persistieren — sonst läge der Key im
            // Klartext in den Prefs. Auf den Default zurückfallen.
            when {
                value.isBlank() -> p.defaultModel
                looksLikeApiKey(value) -> p.defaultModel
                else -> value
            },
        )
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
