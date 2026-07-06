package de.bgg_home.texdroid.ai

/**
 * KI-Anbieter (BYOK). Kapselt die Unterschiede der REST-Chat-APIs, damit die
 * restliche KI-Fläche (Prompt-Feld, Vorschau, Ergebnis-Aktionen, Historie)
 * provider-agnostisch bleibt.
 *
 * [modelPresets] sind nur Vorschläge — das tatsächliche Modell ist in den
 * Einstellungen frei überschreibbar (Modell-IDs ändern sich häufig).
 * Die konkrete Request-/Response-Logik kommt in QW A2.
 */
enum class AiProvider(
    val id: String,
    val displayName: String,
    val defaultModel: String,
    val modelPresets: List<String>,
) {
    ANTHROPIC(
        id = "anthropic",
        displayName = "Anthropic (Claude)",
        defaultModel = "claude-sonnet-5",
        modelPresets = listOf("claude-sonnet-5", "claude-opus-4-8", "claude-haiku-4-5-20251001"),
    ),
    OPENAI(
        id = "openai",
        displayName = "OpenAI (ChatGPT)",
        defaultModel = "gpt-4o",
        modelPresets = listOf("gpt-4o", "gpt-4o-mini", "gpt-4.1"),
    ),
    GEMINI(
        id = "gemini",
        displayName = "Google (Gemini)",
        defaultModel = "gemini-2.0-flash",
        modelPresets = listOf("gemini-2.0-flash", "gemini-1.5-pro", "gemini-1.5-flash"),
    ),
    ;

    companion object {
        fun fromId(id: String?): AiProvider = entries.firstOrNull { it.id == id } ?: ANTHROPIC
    }
}
