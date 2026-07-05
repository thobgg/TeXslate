package de.bgg_home.texdroid.editor

import de.bgg_home.texdroid.compile.CompileError
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * Editor-Aktionen für QW 3.2 (Jump-to-Error + Fehlerzeilen markieren).
 * Als Extensions auf [CodeEditor] gehalten, damit die UI sie direkt aufrufen kann.
 */

/**
 * Springt zur (1-basierten) [line1Based], scrollt sie in den sichtbaren Bereich
 * und setzt den Cursor an ihren Anfang.
 */
fun CodeEditor.jumpToErrorLine(line1Based: Int) {
    val lastLine = (text.lineCount - 1).coerceAtLeast(0)
    val target = (line1Based - 1).coerceIn(0, lastLine)
    setSelection(target, 0) // scrollt hin (makeItVisible) und setzt den Cursor
    requestFocus()
}

/**
 * Markiert die [errors] mit Zeilennummer als Fehler im Editor (rote
 * Unterschlängelung über die Diagnostics-API). Ohne Zeilennummer oder bei
 * leerer Liste werden die Markierungen zurückgesetzt.
 */
fun CodeEditor.showErrorDiagnostics(errors: List<CompileError>) {
    val content = text
    val container = DiagnosticsContainer()
    errors.forEach { err ->
        val line0 = (err.line ?: return@forEach) - 1
        if (line0 < 0 || line0 >= content.lineCount) return@forEach
        val start = content.getCharIndex(line0, 0)
        val cols = content.getColumnCount(line0)
        if (cols <= 0) return@forEach // leere Zeile → nichts zu unterschlängeln
        container.addDiagnostic(
            DiagnosticRegion(start, start + cols, DiagnosticRegion.SEVERITY_ERROR),
        )
    }
    setDiagnostics(container)
}
