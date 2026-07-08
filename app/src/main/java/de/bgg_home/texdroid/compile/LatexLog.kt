package de.bgg_home.texdroid.compile

/**
 * Parser für TeX-/Tectonic-Logs. Zieht aus dem rohen Log die Einzelfehler mit
 * (soweit vorhanden) Quelltext-Zeile und Meldung heraus.
 *
 * TeX kennt keine einheitliche, maschinenlesbare Fehlerausgabe – wir decken die
 * zwei verbreiteten Formen ab:
 *
 *  1. Klassisch (XeTeX/TeX):
 *         ! Undefined control sequence.
 *         l.12 \foo
 *     → "!"-Zeile = Meldung, die spätere "l.<n>"-Zeile = Zeilennummer.
 *
 *  2. file:line:-Form (u.a. manche LaTeX-Fehler):
 *         ./document.tex:12: LaTeX Error: \begin{itemize} on input line 12 ended ...
 *
 * Bewusst tolerant gehalten: lieber eine grobe, brauchbare Liste als eine
 * perfekte, die an Sonderfällen scheitert. Die feine Zuordnung Fehler↔Editorzeile
 * (Jump-to-Error) ist ein M3-Thema.
 */
object LatexLog {

    // "./document.tex:12: <meldung>"  oder  "document.tex:12: <meldung>"
    private val fileLineRegex = Regex("""^\.?/?[^:\n]*\.tex:(\d+):\s*(.*)$""")
    // "l.12 <resttext>"  – TeX zeigt so die aktuelle Eingabezeile.
    private val texLineRegex = Regex("""^l\.(\d+)\s?(.*)$""")

    fun parseErrors(log: String): List<CompileError> {
        if (log.isBlank()) return emptyList()
        val lines = log.lines()
        val out = ArrayList<CompileError>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            // Form 2: file.tex:line: message
            val fileMatch = fileLineRegex.find(line.trim())
            if (fileMatch != null) {
                val lineNo = fileMatch.groupValues[1].toIntOrNull()
                val msg = LatexErrorGerman.translate(fileMatch.groupValues[2].trim().ifEmpty { LatexErrorGerman.fallback() })
                out += CompileError(lineNo, msg)
                i++
                continue
            }

            // Form 1: "! message" ... später "l.<n>"
            if (line.startsWith("!")) {
                val msg = LatexErrorGerman.translate(line.removePrefix("!").trim().ifEmpty { LatexErrorGerman.fallback() })
                // In den nächsten paar Zeilen nach der zugehörigen "l.<n>" suchen.
                var lineNo: Int? = null
                var j = i + 1
                val lookaheadEnd = minOf(lines.size, i + 8)
                while (j < lookaheadEnd) {
                    val m = texLineRegex.find(lines[j].trimStart())
                    if (m != null) {
                        lineNo = m.groupValues[1].toIntOrNull()
                        break
                    }
                    j++
                }
                out += CompileError(lineNo, msg)
                i++
                continue
            }
            i++
        }
        return out
    }
}
