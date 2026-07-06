package de.bgg_home.texdroid.editor

import de.bgg_home.texdroid.compile.CompileError
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher

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
 * Springt zur (1-basierten) Zeile [line1Based] (Editor-Komfort „Gehe zu Zeile").
 * Nutzt dieselbe Logik wie der Fehler-Sprung: Zeile sichtbar machen + Cursor an
 * ihren Anfang.
 */
fun CodeEditor.goToLine(line1Based: Int) = jumpToErrorLine(line1Based)

/**
 * Kommentar ein/aus (Editor-Komfort) für die aktuelle Zeile bzw. alle Zeilen der
 * Auswahl. Sind alle nicht-leeren Zeilen bereits mit `%` auskommentiert, werden
 * sie ent-kommentiert – sonst werden alle mit `% ` auskommentiert. Leere Zeilen
 * bleiben unberührt. Läuft in einem Batch-Edit (ein Undo-Schritt).
 */
fun CodeEditor.toggleLineComment() {
    val c = cursor
    val startLine = c.leftLine
    // Endet die Auswahl am Zeilenanfang, gehört diese letzte Zeile nicht dazu.
    var endLine = c.rightLine
    if (endLine > startLine && c.rightColumn == 0) endLine--

    val content = text
    var anyNonBlank = false
    var allCommented = true
    for (line in startLine..endLine) {
        val s = content.getLineString(line)
        if (s.isBlank()) continue
        anyNonBlank = true
        if (!s.trimStart().startsWith("%")) {
            allCommented = false
            break
        }
    }
    if (!anyNonBlank) return

    content.beginBatchEdit()
    try {
        for (line in startLine..endLine) {
            val s = content.getLineString(line)
            if (s.isBlank()) continue
            if (allCommented) {
                val at = s.indexOfFirst { !it.isWhitespace() } // Position des '%'
                var removeTo = at + 1
                if (removeTo < s.length && s[removeTo] == ' ') removeTo++ // ein Leerzeichen mit
                content.delete(line, at, line, removeTo)
            } else {
                content.insert(line, 0, "% ")
            }
        }
    } finally {
        content.endBatchEdit()
    }
    requestFocus()
}

/**
 * Markiert die [errors] mit Zeilennummer als Fehler im Editor (rote
 * Unterschlängelung über die Diagnostics-API). Ohne Zeilennummer oder bei
 * leerer Liste werden die Markierungen zurückgesetzt.
 */
/** Der aktuell markierte Text – leer, wenn nichts selektiert ist. */
fun CodeEditor.selectedText(): String {
    val c = cursor
    return if (c.isSelected) text.subSequence(c.left, c.right).toString() else ""
}

/**
 * Fügt [snippet] als eigenständigen Block direkt **vor** dem letzten
 * `\end{document}` ein (mit Leerzeile davor), damit KI-generierter Inhalt im
 * Dokumentkörper landet – ohne dass der Cursor manuell gesetzt werden muss.
 * Fehlt `\end{document}`, wird am Cursor eingefügt.
 */
fun CodeEditor.insertBeforeEndDocument(snippet: String) {
    val block = snippet.trim() + "\n\n"
    val full = text.toString()
    val idx = full.lastIndexOf("\\end{document}")
    if (idx < 0) {
        insertText(block, block.length)
        return
    }
    val before = full.substring(0, idx)
    val line = before.count { it == '\n' }
    val column = idx - (before.lastIndexOf('\n') + 1)
    text.insert(line, column, block)
    setSelection(line, column) // Cursor an den Anfang des eingefügten Blocks.
    requestFocus()
}

/**
 * Suchen & Ersetzen (Editor-Komfort). Nutzt die eingebaute Such-Engine von
 * sora-editor (`EditorSearcher`); wir binden nur die UI an.
 */

/**
 * Startet/aktualisiert die Suche. Leere [query] beendet die Suche (Markierungen
 * verschwinden). Gibt `false` zurück, wenn [regex] aktiv ist und das Muster
 * ungültig ist – die UI zeigt das als Hinweis. [caseInsensitive] = Groß/Klein egal.
 */
fun CodeEditor.runSearch(query: String, caseInsensitive: Boolean, regex: Boolean): Boolean {
    if (query.isEmpty()) {
        stopSearch()
        return true
    }
    // Bei Regex das Muster vorab prüfen (sora wirft nicht, sondern liefert 0
    // Treffer) – so kann die UI ein ungültiges Muster als solches anzeigen.
    if (regex && runCatching { java.util.regex.Pattern.compile(query) }.isFailure) {
        stopSearch()
        return false
    }
    val type = if (regex) {
        EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION
    } else {
        EditorSearcher.SearchOptions.TYPE_NORMAL
    }
    return runCatching {
        searcher.search(query, EditorSearcher.SearchOptions(type, caseInsensitive))
    }.isSuccess
}

/** Zum nächsten Treffer springen (zyklisch, sobald eine Suche aktiv ist). */
fun CodeEditor.searchNext() {
    if (searcher.hasQuery()) searcher.gotoNext()
}

/** Zum vorigen Treffer springen. */
fun CodeEditor.searchPrevious() {
    if (searcher.hasQuery()) searcher.gotoPrevious()
}

/** Aktuellen Treffer durch [replacement] ersetzen und weiterspringen. */
fun CodeEditor.replaceCurrentMatch(replacement: String) {
    if (searcher.hasQuery()) runCatching { searcher.replaceCurrentMatch(replacement) }
}

/** Alle Treffer durch [replacement] ersetzen. */
fun CodeEditor.replaceAllMatches(replacement: String) {
    if (searcher.hasQuery()) runCatching { searcher.replaceAll(replacement) }
}

/** Suche beenden und Trefferhervorhebung entfernen (inkl. Neuzeichnen). */
fun CodeEditor.stopSearch() {
    searcher.stopSearch()
    invalidate() // stopSearch räumt den Zustand, zeichnet aber nicht neu.
}

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
