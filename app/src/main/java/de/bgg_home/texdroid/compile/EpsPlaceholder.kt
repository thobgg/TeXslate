package de.bgg_home.texdroid.compile

/**
 * Ersetzt EPS-Abbildungen durch einen beschrifteten Platzhalter, damit das
 * Dokument **trotzdem kompiliert**.
 *
 * EPS ist kein Bildformat, sondern ein PostScript-Programm; es einzubetten
 * bräuchte einen PostScript-Interpreter (Ghostscript), den es auf Android nicht
 * gibt. Bisher war deshalb bei solchen Dokumenten Schluss — dabei will man
 * unterwegs meist am Text arbeiten und nicht an den Abbildungen. Also: Rahmen mit
 * Dateiname an die Bildstelle, der Rest wird ganz normal gesetzt. Text, Formeln,
 * Verweise und Seitenzahlen stimmen.
 *
 * Wie überall gilt: geändert wird nur die **Compile-Kopie**, nie die Datei des
 * Nutzers. Ersetzt wird innerhalb der Zeile und die Makro-Definition hängt sich an
 * die vorhandene `\begin{document}`-Zeile — so bleiben alle Zeilennummern gültig.
 *
 * Liegt neben `bild.eps` auch `bild.pdf`, greift das hier gar nicht: LaTeX nimmt
 * von sich aus die PDF-Fassung (siehe [GraphicsCheck]).
 */
object EpsPlaceholder {

    /** Name des eingefügten Makros — bewusst sperrig, damit nichts kollidiert. */
    private const val MACRO = "TeXslateEpsPlaceholder"

    /**
     * `\includegraphics[…]{datei}` — Gruppe 1 ist der Dateiname. Klammern
     * durchgängig maskiert (Androids ICU-Regex lehnt unmaskierte `}`/`]` ab).
     */
    private val INCLUDE_GRAPHICS = Regex(
        """\\includegraphics\s*\*?\s*(?:\[[^\]]*\])*\s*\{([^\{\}]*)\}""",
    )

    /** `\begin{document}` – dort hängen wir die Makro-Definition an. */
    private val BEGIN_DOCUMENT = Regex("""\\begin\s*\{\s*document\s*\}""")

    /**
     * @param rewritten umgeschriebener Quelltext, oder null wenn die Ersetzung nicht
     *   möglich war (kein `\begin{document}` gefunden) – dann muss der Aufrufer den
     *   erklärenden Fehler zeigen.
     * @param figures die betroffenen EPS-Dateien.
     */
    data class Result(val rewritten: String?, val figures: List<String>)

    /**
     * Baut die Compile-Kopie mit Platzhaltern.
     *
     * @param label Überschrift im Rahmen (lokalisiert, ohne TeX-Sonderzeichen).
     */
    fun withPlaceholders(source: String, filesInJob: Collection<String>, label: String): Result {
        val figures = GraphicsCheck.epsFigures(source, filesInJob)
        if (figures.isEmpty()) return Result(source, emptyList())

        val lower = filesInJob.map { it.lowercase() }.toSet()
        var replaced = false
        val out = StringBuilder(source.length + 256)
        var last = 0
        INCLUDE_GRAPHICS.findAll(source).forEach { m ->
            val ref = m.groupValues[1].trim()
            if (!isEps(ref, lower)) return@forEach
            val shown = if (ref.substringAfterLast('/').contains('.')) ref else "$ref.eps"
            replaced = true
            out.append(source, last, m.range.first).append("\\$MACRO{$shown}")
            last = m.range.last + 1
        }
        if (!replaced) return Result(source, emptyList())
        out.append(source, last, source.length)

        val withMacro = defineMacro(out.toString(), label) ?: return Result(null, figures)
        return Result(withMacro, figures)
    }

    private fun isEps(ref: String, filesLower: Set<String>): Boolean {
        val n = ref.lowercase()
        if (n.endsWith(".eps") || n.endsWith(".ps")) return true
        return !n.substringAfterLast('/').contains('.') && "$n.eps" in filesLower
    }

    /**
     * Hängt die Makro-Definition an die `\begin{document}`-Zeile. `\detokenize`
     * schützt Dateinamen mit `_` & Co., die im Textmodus sonst einen Fehler geben.
     */
    private fun defineMacro(source: String, label: String): String? {
        val m = BEGIN_DOCUMENT.find(source) ?: return null
        val definition = "\\providecommand{\\$MACRO}[1]{\\fbox{\\parbox{0.8\\linewidth}" +
            "{\\centering\\vspace{1em}\\textbf{$label}\\\\\\texttt{\\detokenize{#1}}\\vspace{1em}}}}"
        return source.substring(0, m.range.first) + definition +
            source.substring(m.range.first)
    }
}
