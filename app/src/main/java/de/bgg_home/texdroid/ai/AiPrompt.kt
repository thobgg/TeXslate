package de.bgg_home.texdroid.ai

import java.util.Locale

/** Wie viel vom Dokument der KI mitgegeben wird. */
enum class ContextScope { NONE, SELECTION, DOCUMENT }

/**
 * Baut aus Nutzerfrage und (optionalem) Editor-Kontext den finalen Prompt. Der
 * Kontext wird klar abgegrenzt angehängt, damit die KI Frage von Quelltext
 * unterscheiden kann. Genau dieser Text wird im Vorschau-Dialog gezeigt.
 *
 * Sprache folgt der Geräte-Locale: Deutsch → deutsche Antworten, sonst Englisch.
 * (Kein Context nötig – die AI-Schicht ist reine Logik; [Locale.getDefault]
 * spiegelt die App-/Systemsprache wider.)
 */
object AiPrompt {

    private val german: Boolean get() = Locale.getDefault().language == "de"

    val SYSTEM: String
        get() = if (german) {
            "Du bist ein präziser LaTeX-Assistent in einer Editor-App auf einem Android-Tablet. " +
                "Antworte knapp, praktisch und auf Deutsch. Lieferst du LaTeX-Code, gib nur den " +
                "nötigen Code aus (ohne umschließendes \\documentclass/\\begin{document}, außer es " +
                "wird ein ganzes Dokument verlangt) und erkläre nur, wenn ausdrücklich gefragt."
        } else {
            "You are a precise LaTeX assistant in an editor app on an Android tablet. " +
                "Answer concisely and practically in English. If you provide LaTeX code, output " +
                "only the necessary code (without a surrounding \\documentclass/\\begin{document}, " +
                "unless a whole document is requested) and explain only when explicitly asked."
        }

    private fun contextMarker(scope: ContextScope): String = if (german) {
        when (scope) {
            ContextScope.NONE -> "Ohne Kontext"
            ContextScope.SELECTION -> "Markierung"
            ContextScope.DOCUMENT -> "Ganzes Dokument"
        }
    } else {
        when (scope) {
            ContextScope.NONE -> "No context"
            ContextScope.SELECTION -> "Selection"
            ContextScope.DOCUMENT -> "Whole document"
        }
    }

    fun build(question: String, scope: ContextScope, selection: String, document: String): String {
        val context = when (scope) {
            ContextScope.NONE -> ""
            ContextScope.SELECTION -> selection
            ContextScope.DOCUMENT -> document
        }
        return if (context.isBlank()) {
            question
        } else {
            "$question\n\n--- LaTeX context (${contextMarker(scope)}) ---\n$context"
        }
    }
}
