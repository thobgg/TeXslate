package de.bgg_home.texdroid.compile

/**
 * Parser für TeX-/Tectonic-Logs. Zieht aus dem rohen Log die Einzelfehler mit
 * (soweit vorhanden) Datei, Quelltext-Zeile und Meldung heraus.
 *
 * TeX kennt keine einheitliche, maschinenlesbare Fehlerausgabe – wir decken die
 * verbreiteten Formen ab:
 *
 *  1. Klassisch (XeTeX/TeX):
 *         ! Undefined control sequence.
 *         l.12 \foo
 *     → "!"-Zeile = Meldung, die spätere "l.<n>"-Zeile = Zeilennummer.
 *
 *  2. file:line:-Form (u.a. manche LaTeX-Fehler):
 *         ./document.tex:12: LaTeX Error: … on input line 12 …
 *
 * **Datei-Zuordnung:** TeX signalisiert Dateiwechsel über die Klammer-Notation
 * `(pfad …)` im Log (verschachtelt). Wir führen daraus einen Datei-Stack mit;
 * die oberste offene Datei zum Fehlerzeitpunkt ist die fehlerhafte Datei. Ohne
 * das würde ein Fehler in einer geladenen `.cls`/`.sty` fälschlich dem offenen
 * Hauptdokument zugeschrieben (beobachteter Bug B).
 *
 * **fontspec-Zeilenversatz (Bug A):** `\setsansfont{…}` liest über das Zeilenende
 * hinaus voraus (optionales `[…]`-Argument); beim Auslösen des „font cannot be
 * found"-Fehlers steht TeX schon auf der NÄCHSTEN Zeile, das `l.<n>` zeigt also
 * daneben. Ist der Quelltext bekannt, führen wir den Font-Namen aus der Meldung
 * auf die tatsächliche `\set*font{…}`-Zeile zurück.
 *
 * Bewusst tolerant/pragmatisch: lieber eine grobe, brauchbare Liste als ein
 * perfekter Parser, der an TeX-Sonderfällen scheitert. Grenzen: umbrochene
 * (>79 Zeichen) Dateinamen und Klammern in Fehler-Hilfetexten können den
 * Datei-Stack in Randfällen leicht verfälschen.
 */
object LatexLog {

    // "./document.tex:12: <meldung>" (auch .cls/.sty/.ltx)
    private val fileLineRegex = Regex("""^\.?/?([^:\n]*\.(?:tex|cls|sty|ltx|def)):(\d+):\s*(.*)$""")
    // "l.12 <resttext>" – TeX zeigt so die aktuelle Eingabezeile.
    private val texLineRegex = Regex("""^l\.(\d+)\s?(.*)$""")
    // "on input line 12" – die andere gängige XeTeX-/LaTeX-Zeilenangabe.
    private val onInputLineRegex = Regex("""on input line (\d+)""")
    // fontspec: The font "Name" cannot be found
    private val fontNotFoundRegex =
        Regex("""font "([^"]+)" cannot be found""", RegexOption.IGNORE_CASE)
    // Font-setzende Befehle (fontspec) – für das Rückmappen des Font-Namens.
    private val fontCommandRegex =
        Regex("""\\(?:set(?:main|sans|mono)font|setmathfont|newfontfamily|newfontface|fontspec)""")

    /**
     * @param log    das rohe TeX-Log.
     * @param source der kompilierte Quelltext (optional) – ermöglicht das Rückmappen
     *   von fontspec-Font-Fehlern auf die korrekte `\set*font{…}`-Zeile (Bug A).
     */
    fun parseErrors(log: String, source: String? = null): List<CompileError> {
        if (log.isBlank()) return emptyList()
        val lines = log.lines()
        val out = ArrayList<CompileError>()
        // Datei-Stack: pro '(' ein Eintrag (Dateiname oder null für Nicht-Datei-
        // Gruppen wie "(fontspec)"), pro ')' ein Pop. So bleiben ALLE Klammern
        // balanciert und die aktuelle Datei = oberster nicht-null Eintrag.
        val fileStack = ArrayDeque<String?>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            // Erst den Datei-Stack aus DIESER Zeile nachziehen (die Klammern stehen
            // im Log vor der zugehörigen "!"-/"l."-Ausgabe).
            updateFileStack(line, fileStack)
            val currentFile = fileStack.lastOrNull { it != null }

            // Form 2: file.ext:line: message
            val fileMatch = fileLineRegex.find(line.trim())
            if (fileMatch != null) {
                val fileName = fileMatch.groupValues[1]
                val lineNo = fileMatch.groupValues[2].toIntOrNull()
                val msg = LatexErrorGerman.translate(
                    fileMatch.groupValues[3].trim().ifEmpty { LatexErrorGerman.fallback() },
                )
                out += CompileError(lineNo, msg, fileName)
                i++
                continue
            }

            // Form 1: "! message" … später "l.<n>"
            if (line.startsWith("!")) {
                val rawMsg = collectMessage(lines, i)
                val msg = LatexErrorGerman.translate(
                    rawMsg.ifEmpty { LatexErrorGerman.fallback() },
                )

                // Zeilennummer bestimmen – Priorität:
                //  (1) fontspec-Font-Fehler → Font-Name auf Quelltextzeile mappen,
                //  (2) "on input line N" in der Meldung,
                //  (3) "l.N" im Nachlauf.
                var lineNo: Int? = null
                fontNotFoundRegex.find(rawMsg)?.let { fm ->
                    lineNo = fontLineInSource(fm.groupValues[1], source)
                }
                if (lineNo == null) {
                    lineNo = onInputLineRegex.find(rawMsg)?.groupValues?.get(1)?.toIntOrNull()
                }
                if (lineNo == null) {
                    lineNo = lookaheadTexLine(lines, i)
                }

                out += CompileError(lineNo, msg, currentFile)
                i++
                continue
            }
            i++
        }
        return out
    }

    /**
     * Name der Schrift aus einer fontspec-Meldung („The font "X" cannot be found"),
     * sonst null. Rein und JVM-testbar; die verständliche Ersatzmeldung baut
     * [LatexCompiler], weil dafür Gerätepfade und das Font-Verzeichnis nötig sind.
     */
    fun fontNotFoundName(message: String): String? =
        fontNotFoundRegex.find(message)?.groupValues?.get(1)

    /** Klammern der Zeile in den Datei-Stack einarbeiten (push je '(', pop je ')'). */
    private fun updateFileStack(line: String, stack: ArrayDeque<String?>) {
        var idx = 0
        while (idx < line.length) {
            when (line[idx]) {
                '(' -> {
                    var j = idx + 1
                    while (j < line.length && line[j] != ' ' && line[j] != '\t' &&
                        line[j] != '(' && line[j] != ')'
                    ) {
                        j++
                    }
                    val token = line.substring(idx + 1, j)
                    stack.addLast(if (looksLikeFile(token)) token else null)
                    idx = j
                }
                ')' -> {
                    if (stack.isNotEmpty()) stack.removeLast()
                    idx++
                }
                else -> idx++
            }
        }
    }

    /** Heuristik: sieht das Token nach '(' wie ein Dateiname aus (vs. "(fontspec)")? */
    private fun looksLikeFile(token: String): Boolean =
        token.isNotEmpty() && !token.contains('=') &&
            (token.contains('/') || Regex("""\.\w{1,4}$""").containsMatchIn(token))

    /**
     * Meldungstext ab der "!"-Zeile sammeln. Endet die "!"-Zeile mit ":" (z.B.
     * „! Package fontspec Error:"), wird die erste Fortsetzungszeile angehängt
     * (Präfix wie „(fontspec)" entfernt) – dort steht bei fontspec der Font-Name.
     */
    private fun collectMessage(lines: List<String>, at: Int): String {
        val head = lines[at].removePrefix("!").trim()
        if (!head.endsWith(":") || at + 1 >= lines.size) return head
        val cont = lines[at + 1]
            .replaceFirst(Regex("""^\s*\([^)]*\)\s*"""), "")
            .trim()
        return if (cont.isEmpty()) head else "$head $cont"
    }

    /** Nächste "l.<n>"-Zeile im begrenzten Nachlauf finden (klassische Form). */
    private fun lookaheadTexLine(lines: List<String>, at: Int): Int? {
        val end = minOf(lines.size, at + 12)
        var j = at + 1
        while (j < end) {
            texLineRegex.find(lines[j].trimStart())?.let {
                return it.groupValues[1].toIntOrNull()
            }
            j++
        }
        return null
    }

    /**
     * Quelltextzeile (1-basiert) suchen, die den Font per fontspec-Befehl setzt –
     * z.B. `\setsansfont{Latin Modern Sans}`. Fallback: irgendeine Zeile, die den
     * Namen in geschweiften Klammern nennt. Null, wenn kein Quelltext/kein Treffer.
     */
    private fun fontLineInSource(fontName: String, source: String?): Int? {
        if (source == null) return null
        source.lineSequence().forEachIndexed { idx, raw ->
            val code = raw.substringBefore('%')
            if (code.contains(fontName) && fontCommandRegex.containsMatchIn(code)) {
                return idx + 1
            }
        }
        source.lineSequence().forEachIndexed { idx, raw ->
            if (raw.substringBefore('%').contains("{$fontName}")) return idx + 1
        }
        return null
    }
}
