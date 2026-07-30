package de.bgg_home.texdroid.compile

/**
 * Macht Dokumente kompilierbar, die für **pdfLaTeX** geschrieben wurden.
 *
 * Tectonic ist XeTeX. Zwei Angewohnheiten aus der pdfLaTeX-Welt brechen den
 * Compile sofort, und beide stehen in unzähligen echten Dokumenten:
 *
 *  1. `\usepackage[latin1]{inputenc}` → `! Package inputenc Error: inputenc is not
 *     designed for xetex or luatex.` XeTeX liest ohnehin UTF-8, das Paket ist
 *     überflüssig.
 *  2. Fest verdrahtete Treiberoptionen: `\usepackage[pdftex]{hyperref}` →
 *     `! Package hyperref Error: Wrong driver option 'pdftex'`. Gemeint ist
 *     „der Treiber der aktuellen Engine", unter XeTeX also `xetex`.
 *
 * Beides wird in der **Compile-Kopie** bereinigt, nie in der Datei des Nutzers —
 * niemand soll sein Dokument an die App anpassen müssen. Ersetzt wird jeweils nur
 * innerhalb der Zeile, also bleiben Zeilennummern für das Fehlerpanel gültig.
 *
 * Bewusst NICHT angetastet: `fontenc`. `\usepackage[T1]{fontenc}` funktioniert
 * unter XeTeX und ist in vielen Dokumenten sinnvoll gesetzt.
 */
object EngineCompat {

    /** Was angepasst wurde — die Meldungstexte baut [LatexCompiler] (i18n). */
    enum class Adaptation { INPUTENC_ENTFERNT, TREIBER_UMGESTELLT }

    /**
     * `\usepackage[…]{inputenc}` bzw. `\RequirePackage[…]{inputenc}`.
     *
     * ⚠️ Klammern durchgängig maskiert, auch in Zeichenklassen: Androids
     * ICU-Regex-Engine lehnt unmaskierte `}`/`]` ab, Desktop-Java toleriert sie
     * (dieselbe Falle wie bei [FontFallback] und der biblatex-Regex).
     */
    private val INPUTENC = Regex(
        """\\(?:usepackage|RequirePackage)\s*(?:\[[^\]]*\])?\s*\{\s*inputenc\s*\}""",
    )

    /** Optionsliste von `\usepackage[…]{…}` / `\documentclass[…]{…}`; Gruppe 1 = Optionen. */
    private val PACKAGE_OPTIONS = Regex(
        """\\(?:usepackage|RequirePackage|documentclass)\s*\[([^\]]*)\]""",
    )

    /** Treiber, die unter XeTeX falsch sind, samt Ersatz. */
    private val WRONG_DRIVERS = setOf("pdftex", "dvips", "dvipdfm", "dvipdfmx", "luatex")
    private const val XETEX = "xetex"

    /**
     * Passt [source] für XeTeX an.
     *
     * @return angepasster Quelltext und die Liste der Eingriffe — oder null, wenn
     *   nichts zu tun war (der Normalfall bei Dokumenten, die für XeTeX geschrieben
     *   sind; dann wird auch nichts kopiert).
     */
    fun adapt(source: String): Pair<String, List<Adaptation>>? {
        val adaptations = mutableListOf<Adaptation>()

        var out = INPUTENC.replace(source) {
            adaptations += Adaptation.INPUTENC_ENTFERNT
            ""
        }

        out = replaceDriverOptions(out) { adaptations += Adaptation.TREIBER_UMGESTELLT }

        if (adaptations.isEmpty()) return null
        return out to adaptations.distinct()
    }

    /**
     * Ersetzt falsche Treiber-Tokens INNERHALB der Optionsklammern. Nur dort, damit
     * ein Paketname wie `pdftexcmds` oder Text im Dokument unberührt bleibt.
     */
    private fun replaceDriverOptions(source: String, onChange: () -> Unit): String {
        val out = StringBuilder(source.length)
        var last = 0
        PACKAGE_OPTIONS.findAll(source).forEach { m ->
            val group = m.groups[1] ?: return@forEach
            val rewritten = group.value.split(',').joinToString(",") { raw ->
                val token = raw.trim()
                if (token.lowercase() in WRONG_DRIVERS) raw.replace(token, XETEX) else raw
            }
            if (rewritten == group.value) return@forEach
            onChange()
            out.append(source, last, group.range.first).append(rewritten)
            last = group.range.last + 1
        }
        if (last == 0) return source
        out.append(source, last, source.length)
        return out.toString()
    }
}
