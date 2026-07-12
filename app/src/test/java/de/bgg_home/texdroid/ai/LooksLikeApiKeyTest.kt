package de.bgg_home.texdroid.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Schützt die Heuristik, die einen versehentlich ins Modell-Feld getippten
 * API-Key erkennt (sonst läge er unverschlüsselt als Klartext-Modellname vor).
 */
class LooksLikeApiKeyTest {

    @Test
    fun anthropicKey_wirdErkannt() {
        assertTrue(looksLikeApiKey("sk-ant-api03-" + "x".repeat(95)))
    }

    @Test
    fun openaiKey_wirdErkannt() {
        assertTrue(looksLikeApiKey("sk-proj-abcDEF1234567890"))
    }

    @Test
    fun googleKey_wirdErkannt() {
        assertTrue(looksLikeApiKey("AIzaSyD-abcDEF1234567890xyz"))
    }

    @Test
    fun sehrLangerWert_wirdErkannt() {
        assertTrue(looksLikeApiKey("a".repeat(80)))
    }

    @Test
    fun echteModellnamen_sindOk() {
        assertFalse(looksLikeApiKey("claude-sonnet-5"))
        assertFalse(looksLikeApiKey("claude-opus-4-8"))
        assertFalse(looksLikeApiKey("claude-haiku-4-5-20251001"))
        assertFalse(looksLikeApiKey("gpt-4o"))
        assertFalse(looksLikeApiKey("gemini-2.0-flash"))
    }

    @Test
    fun leererWert_istKeinKey() {
        assertFalse(looksLikeApiKey(""))
        assertFalse(looksLikeApiKey("   "))
    }

    @Test
    fun fuehrendeLeerzeichen_werdenBeruecksichtigt() {
        assertTrue(looksLikeApiKey("  sk-ant-api03-secret  "))
    }
}
