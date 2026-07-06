package de.bgg_home.texdroid.ai

/** Wie viel vom Dokument der KI mitgegeben wird. */
enum class ContextScope(val label: String) {
    NONE("Ohne Kontext"),
    SELECTION("Markierung"),
    DOCUMENT("Ganzes Dokument"),
}

/**
 * Baut aus Nutzerfrage und (optionalem) Editor-Kontext den finalen Prompt. Der
 * Kontext wird klar abgegrenzt angehängt, damit die KI Frage von Quelltext
 * unterscheiden kann. Genau dieser Text wird im Vorschau-Dialog gezeigt.
 */
object AiPrompt {
    const val SYSTEM =
        "Du bist ein präziser LaTeX-Assistent in einer Editor-App auf einem Android-Tablet. " +
            "Antworte knapp, praktisch und auf Deutsch. Lieferst du LaTeX-Code, gib nur den " +
            "nötigen Code aus (ohne umschließendes \\documentclass/\\begin{document}, außer es " +
            "wird ein ganzes Dokument verlangt) und erkläre nur, wenn ausdrücklich gefragt."

    fun build(question: String, scope: ContextScope, selection: String, document: String): String {
        val context = when (scope) {
            ContextScope.NONE -> ""
            ContextScope.SELECTION -> selection
            ContextScope.DOCUMENT -> document
        }
        return if (context.isBlank()) {
            question
        } else {
            "$question\n\n--- LaTeX-Kontext (${scope.label}) ---\n$context"
        }
    }
}
